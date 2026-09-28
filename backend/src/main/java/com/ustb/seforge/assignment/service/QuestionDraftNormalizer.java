package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ustb.seforge.content.service.DocumentParserService.ParsedSection;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Import-only compatibility boundary. Never accepts model-provided authorization or resource IDs. */
final class QuestionDraftNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FIELDS = Set.of("type","questionType","contentMarkdown","prompt","stem","question",
            "choices","options","correctChoiceIndexes","correctAnswer","answer","booleanAnswer","referenceAnswer","solution",
            "answerEvidence","score","points","scoreEvidence","sourcePage","page","boundingBox","sourceRegion","warning","warnings");
    static final class Invalid extends IllegalArgumentException {
        final int question; final String field; final String reason;
        Invalid(int question,String field,String reason) { super("Question draft validation failed");this.question=question;this.field=field;this.reason=reason; }
    }
    static JsonNode normalize(JsonNode root,List<ParsedSection> sources) {
        if(root!=null && root.isObject() && root.size()==1 && root.has("json")) {
            try { root=JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(root.get("json").textValue()); }
            catch(Exception e){throw new Invalid(0,"json","INVALID_JSON");}
        }
        JsonNode questions;
        if(root!=null && root.isArray()) questions=root;
        else { allowed(root,Set.of("questions"),0);questions=root.get("questions"); }
        if(questions==null||!questions.isArray()||questions.isEmpty()||questions.size()>50)throw new Invalid(0,"questions","EXPECTED_1_TO_50");
        var result=JSON.createObjectNode();var output=result.putArray("questions");
        int index=0;
        for(var q:questions)output.add(question(q,++index,sources));
        return result;
    }
    private static ObjectNode question(JsonNode q,int number,List<ParsedSection> sources) {
        allowed(q,FIELDS,number);var out=JSON.createObjectNode();var warnings=new ArrayList<String>();
        String type=type(text(pick(q,number,"type","questionType"),number,"type"));
        if(type.equals("UNKNOWN"))warnings.add("题型不明确，请教师选择");
        out.put("type",type);
        String body=text(pick(q,number,"contentMarkdown","prompt","stem","question"),number,"contentMarkdown");
        if(body.isBlank())throw new Invalid(number,"contentMarkdown","EMPTY_QUESTION");
        out.put("contentMarkdown",body);
        var options=pick(q,number,"choices","options");var choices=out.putArray("choices");var labels=new LinkedHashMap<String,Integer>();
        if(!options.isMissingNode()&&!options.isNull()) {
            if(!options.isArray()&&!options.isObject())throw new Invalid(number,"choices","INVALID_OPTIONS");
            if(options.size()>30)throw new Invalid(number,"choices","TOO_MANY_OPTIONS");
            var entries=new ArrayList<Map.Entry<String,JsonNode>>();
            if(options.isObject())options.fields().forEachRemaining(entries::add);
            else for(int i=0;i<options.size();i++)entries.add(Map.entry(String.valueOf((char)('A'+i)),options.get(i)));
            for(var entry:entries) {
                var option=entry.getValue();String label=entry.getKey();String content;
                if(option.isObject()) {
                    allowed(option,Set.of("id","key","label","text","content","value"),number);
                    content=text(pick(option,number,"text","content","value"),number,"choices");
                    String supplied=text(pick(option,number,"id","key"),number,"choices");
                    String display=text(option.path("label"),number,"choices");
                    if(content.isBlank()){content=display;} else if(!display.isBlank())label=display;
                    if(!supplied.isBlank())putLabel(labels,supplied,choices.size(),number);
                } else content=text(option,number,"choices");
                if(content.isBlank())throw new Invalid(number,"choices","EMPTY_OPTION");
                putLabel(labels,label,choices.size(),number);
                choices.add(content.replaceFirst("^[A-Z][.、．)）]\\s*", ""));
            }
        }
        boolean choice=type.equals("SINGLE_CHOICE")||type.equals("MULTIPLE_CHOICE");
        if(choice&&choices.size()<2)warnings.add("选项不完整，请补充后确认");
        var indices=out.putArray("correctChoiceIndexes");
        var explicit=q.path("correctChoiceIndexes");var answer=pick(q,number,"correctAnswer","answer");
        String reference=text(pick(q,number,"referenceAnswer","solution"),number,"referenceAnswer");
        boolean badAnswer=false;
        if(choice) {
            if(explicit.isArray()&&!explicit.isEmpty()) {
                for(var item:explicit) {try { int n=Integer.parseInt(item.asText());if(n<0||n>=choices.size()||!item.asText().matches("[0-9]+"))badAnswer=true;else indices.add(n); } catch(NumberFormatException e){badAnswer=true;} }
            } else if(!explicit.isMissingNode()&&!explicit.isNull()&&!explicit.isArray())badAnswer=true;
            if(indices.isEmpty()&&!answer.isMissingNode()&&!answer.isNull()) {
                List<String> tokens=new ArrayList<>();
                if(answer.isArray())for(var item:answer)tokens.add(text(item,number,"correctAnswer"));
                else {String value=text(answer,number,"correctAnswer");boolean matchesText=false;for(var c:choices)if(c.asText().equals(value))matchesText=true;
                    if(matchesText)tokens.add(value);else {if(value.matches("[A-Z]{2,}"))value=String.join(",",value.split(""));tokens.addAll(Arrays.asList(value.split("[,，、;/\\s]+")));}}
                for(String token:tokens) {if(token.isBlank())continue;Integer n=labels.get(token.trim().toUpperCase(Locale.ROOT));
                    if(n==null){for(int i=0;i<choices.size();i++)if(choices.get(i).asText().equals(token)){if(n!=null){n=null;break;}n=i;}}
                    if(n==null)badAnswer=true;else indices.add(n);
                }
            }
            var unique=new LinkedHashSet<Integer>();indices.forEach(n->unique.add(n.intValue()));indices.removeAll();unique.forEach(indices::add);
            if(type.equals("SINGLE_CHOICE")&&indices.size()>1)badAnswer=true;
        }
        String bool="";
        if(type.equals("TRUE_FALSE")) {
            JsonNode value=q.path("booleanAnswer");if(value.isMissingNode()||value.isNull()||value.asText().isBlank())value=answer;
            String raw=text(value,number,"booleanAnswer").trim().toLowerCase(Locale.ROOT);
            bool=switch(raw){case "true","正确","对","是","√"->"true";case "false","错误","错","否","×"->"false";default->"";};
            badAnswer=!raw.isBlank()&&bool.isBlank();
        } else if(!choice&&reference.isBlank()&&!answer.isMissingNode()&&!answer.isNull())reference=text(answer,number,"referenceAnswer");
        String evidence=text(q.path("answerEvidence"),number,"answerEvidence");
        boolean hasAnswer=!indices.isEmpty()||!bool.isBlank()||!reference.isBlank();
        if(badAnswer || (hasAnswer&&!evidenceExists(evidence,sources))) {
            indices.removeAll();bool="";reference="";evidence="";
            warnings.add("答案无法可靠匹配选项或来源，请对照原件补充；系统未自动猜测");
        }
        out.put("booleanAnswer",bool);out.put("referenceAnswer",reference);out.put("answerEvidence",evidence);
        String score=text(pick(q,number,"score","points"),number,"score");String scoreEvidence=text(q.path("scoreEvidence"),number,"scoreEvidence");
        if(!score.isBlank()) {
            try {var n=new BigDecimal(score).stripTrailingZeros();if(n.signum()<=0||n.compareTo(new BigDecimal("100000"))>0||n.scale()>2||!evidenceExists(scoreEvidence,sources)||!belongsToQuestion(scoreEvidence,body))throw new NumberFormatException();score=n.toPlainString();}
            catch(NumberFormatException e){score="";scoreEvidence="";warnings.add("分值或分值来源不明确，请教师填写");}
        }
        out.put("score",score);out.put("scoreEvidence",scoreEvidence);
        var page=pick(q,number,"sourcePage","page");int pageNumber;
        try {if(page.isMissingNode()||page.isNull()) {var pages=sources.stream().map(ParsedSection::page).distinct().toList();if(pages.size()!=1)throw new NumberFormatException();pageNumber=pages.getFirst();warnings.add("来源页按唯一输入页补齐");}else pageNumber=Integer.parseInt(page.asText());}
        catch(NumberFormatException e){throw new Invalid(number,"sourcePage","INVALID_PAGE");}
        if(sources.stream().noneMatch(s->s.page()==pageNumber))throw new Invalid(number,"sourcePage","INVALID_PAGE");out.put("sourcePage",pageNumber);
        var region=pick(q,number,"boundingBox","sourceRegion");var box=out.putArray("boundingBox");
        if(region.isArray()&&region.size()==4){for(var n:region)if(n.isNumber())box.add(n.doubleValue());}
        if(!region.isMissingNode()&&!region.isNull()&&!(region.isArray()&&region.isEmpty())){
            if(box.size()!=4||box.get(0).asDouble()<0||box.get(1).asDouble()<0||box.get(2).asDouble()>1||box.get(3).asDouble()>1||box.get(2).asDouble()<=box.get(0).asDouble()||box.get(3).asDouble()<=box.get(1).asDouble()){box.removeAll();warnings.add("图片区域不可用，请核对完整原图");}
        }
        var warning=pick(q,number,"warning","warnings");if(warning.isArray())for(var w:warning)warnings.add(text(w,number,"warning"));else if(!warning.isMissingNode()&&!warning.isNull())warnings.add(text(warning,number,"warning"));
        out.put("warning",String.join("；",warnings));return out;
    }
    static boolean evidenceExists(String evidence,List<ParsedSection> sources) {
        if(evidence==null||evidence.isBlank())return false;
        String quote=normalizeText(evidence);
        return sources.stream().anyMatch(s->normalizeText(s.text()).contains(quote));
    }
    private static String normalizeText(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\s+","").trim();}
    // A score elsewhere in the document is not evidence for this question. Ambiguity stays editable/unknown.
    private static boolean belongsToQuestion(String evidence,String body) {
        String stem=normalizeText(body).replaceFirst("^\\d+[.、．)）]", "");
        if(stem.isBlank())return false;
        return normalizeText(evidence).contains(stem.substring(0,Math.min(12,stem.length())));
    }
    private static void putLabel(Map<String,Integer> labels,String label,int index,int question){String key=label.trim().toUpperCase(Locale.ROOT);Integer previous=labels.putIfAbsent(key,index);if(previous!=null&&previous!=index)throw new Invalid(question,"choices","DUPLICATE_OPTION_LABEL");}
    private static JsonNode pick(JsonNode node,int question,String... names){JsonNode result=MissingNode.getInstance();for(String name:names){var value=node.get(name);if(value!=null&&!value.isNull()){if(!result.isMissingNode()&&!result.equals(value))throw new Invalid(question,names[0],"CONFLICTING_FIELDS");result=value;}}return result;}
    private static String text(JsonNode value,int q,String field){if(value==null||value.isNull()||value.isMissingNode())return "";if(!value.isValueNode())throw new Invalid(q,field,"EXPECTED_SCALAR");return value.asText();}
    private static void allowed(JsonNode node,Set<String> fields,int question){if(node==null||!node.isObject())throw new Invalid(question,"questions","EXPECTED_OBJECT");node.fieldNames().forEachRemaining(key->{if(!fields.contains(key))throw new Invalid(question,"fields","UNSUPPORTED_FIELD");});}
    private static String type(String value){String key=value.trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');return switch(key){
        case "SINGLE_CHOICE","SINGLE","单选","单选题"->"SINGLE_CHOICE";
        case "MULTIPLE_CHOICE","MULTI_CHOICE","多选","多选题"->"MULTIPLE_CHOICE";
        case "TRUE_FALSE","BOOLEAN","判断","判断题"->"TRUE_FALSE";
        case "SHORT_ANSWER","简答","简答题"->"SHORT_ANSWER";
        case "ANALYSIS","分析","分析题"->"ANALYSIS";
        case "DESIGN","设计","设计题","软件设计"->"DESIGN";
        case "CODE","CODING","代码题","编程题"->"CODE";
        case "DOCUMENT_REPORT","REPORT","文档报告","报告题"->"DOCUMENT_REPORT";
        default->"UNKNOWN";};}
}
