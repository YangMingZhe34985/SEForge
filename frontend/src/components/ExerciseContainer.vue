<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <div class="search-section">
      <h2 class="search-title">
        <i class="fas fa-search"></i>
        智能推荐更多题目
      </h2>
      <div class="search-box">
        <input
            type="text"
            class="search-input"
            placeholder="输入习题内容..."
            v-model="searchQuery"
            @keyup.enter="handleSearch"
        >
        <button class="search-btn" @click="handleSearch">
          <i class="fas fa-bolt"></i> 即刻推荐
        </button>
      </div>
      <div
          class="upload-section"
          id="uploadSection"
          @click="triggerFileInput"
          @dragover.prevent="handleDragOver"
          @dragleave="handleDragLeave"
          @drop.prevent="handleDrop"
      >
        <div class="upload-icon">
          <i :class="uploadIconClass" :style="{color: uploadIconColor}"></i>
        </div>
        <div class="upload-text">{{ uploadText }}</div>
        <div class="upload-hint">支持JPG、PNG格式的习题图片</div>
        <input
            type="file"
            id="fileInput"
            accept="image/*"
            style="display: none;"
            ref="fileInput"
            @change="handleFileChange"
        >
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content-section">
      <!-- 无内容时的引导提示 -->
      <div v-if="!hasContent" class="empty-content">
        <div class="empty-icon">
          <i class="fas fa-cloud-upload-alt"></i>
        </div>
        <div class="empty-title">上传习题开始学习之旅</div>
        <div class="empty-hint">请上传习题图片或输入关键词搜索，系统将为您推荐相关题目和详细解析</div>
        <button class="action-btn" @click="triggerFileInput">
          <i class="fas fa-upload"></i> 上传习题图片
        </button>
      </div>

      <!-- 有内容时的显示 -->
      <div v-else>
        <!-- 推荐详情 -->
        <div class="exercise-detail">
          <div class="exercise-header">
            <h3 class="exercise-title">
              <i class="fas fa-book"></i> {{ currentExercise.title }}
            </h3>
            <div class="exercise-meta">
              <span class="exercise-tag">
                <i class="fas fa-tag"></i> {{ currentExercise.category }}
              </span>
              <span class="exercise-tag">
                <i class="fas fa-chart-line"></i> 难度: {{ currentExercise.difficulty }}
              </span>
              <button class="toggle-btn" @click="showSolution = !showSolution">
                <i class="fas" :class="showSolution ? 'fa-eye-slash' : 'fa-eye'"></i>
                {{ showSolution ? '隐藏解析' : '显示解析' }}
              </button>
            </div>
          </div>

          <div class="exercise-content">
            <div class="question-text" v-html="currentExercise.question"></div>

            <img
                v-if="currentExercise.image"
                :src="currentExercise.image"
                class="question-image"
                alt="习题图片"
            >

            <transition name="fade">
              <div v-show="showSolution" class="solution-section">
                <h4 class="solution-title">
                  <i class="fas fa-lightbulb"></i>
                  解析与答案
                </h4>
                <div class="solution-content" v-html="currentExercise.solution"></div>
              </div>
            </transition>
          </div>
        </div>

        <!-- 更多习题推荐 - 横向三列布局 -->
        <div class="related-exercises">
          <h3 class="related-title">
            <i class="fas fa-link"></i>
            更多习题推荐
          </h3>
          <div class="related-list">
            <div
                class="related-item"
                v-for="(exercise, index) in relatedExercises"
                :key="index"
                @click="selectRelatedExercise(exercise)"
            >
              <div class="related-item-title">{{ exercise.title }}</div>
              <div class="related-item-meta">
                <span class="exercise-tag">
                  <i class="fas fa-tag"></i> {{ exercise.category }}
                </span>
                <span class="exercise-tag">
                  <i class="fas fa-chart-line"></i> 难度: {{ exercise.difficulty }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>
<script>
export default {
  name: 'IntelligentExerciseRecommendation',
  data() {
    return {
      questions: [], // 存储解析后的题库
      searchResults: [],
      currentAnswer: null,
      ocrProgress: 0,
      isProcessing: false,
      searchQuery: '软件测试边界值分析',
      uploadIconClass: 'fas fa-cloud-upload-alt',
      uploadIconColor: '#4a6bff',
      uploadText: '点击或拖拽图片到此处上传',
      showSolution: true,
      hasContent: true,
      currentExercise: {
        id: 'SE20230501',
        title: '边界值分析在软件测试中的应用',
        category: '软件工程',
        difficulty: '中等',
        question: '在软件测试中，什么是边界值分析？请举例说明边界值分析在测试输入字段年龄（有效范围18-60岁）时的应用。',
        solution: `
          <div class="solution-step">
            边界值分析是一种黑盒测试技术，它关注输入域的边界而不是中心。边界值分析基于这样的观察：错误更可能发生在输入域的边界附近而不是中心。
          </div>
          <div class="solution-step">
            对于年龄字段（有效范围18-60岁），边界值分析会测试以下值：
            <ul style="margin-top: 10px; margin-left: 20px;">
              <li>刚好低于最小值：17岁（无效）</li>
              <li>最小值：18岁（有效）</li>
              <li>刚好高于最小值：19岁（有效）</li>
              <li>正常值：30岁（有效）</li>
              <li>刚好低于最大值：59岁（有效）</li>
              <li>最大值：60岁（有效）</li>
              <li>刚好高于最大值：61岁（无效）</li>
            </ul>
          </div>
          <div class="solution-step">
            这种测试方法能有效发现边界条件相关的错误，如错误的比较运算符（如使用了>而不是>=）。
          </div>
        `,
        image: null
      },
      relatedExercises: [
        {
          id: 'SE20230502',
          title: '等价类划分与边界值分析的比较',
          category: '软件工程',
          difficulty: '中等'
        },
        {
          id: 'SE20230503',
          title: '如何为电话号码字段设计测试用例',
          category: '软件测试',
          difficulty: '简单'
        },
        {
          id: 'SE20230504',
          title: '白盒测试中的路径覆盖与边界条件',
          category: '软件质量',
          difficulty: '困难'
        }
      ]
    }
  },
  methods: {
    async handleDocxUpload(file) {
      this.isProcessing = true;
      try {
        this.questions = await parseDocx(file);
        this.$message.success(`题库加载成功，共${this.questions.length}道题目`);
      } catch (err) {
        console.error('解析DOCX失败:', err);
        this.$message.error('题库解析失败，请检查文件格式');
      } finally {
        this.isProcessing = false;
      }
    },

    async handleImageUpload(imageFile) {
      if (this.questions.length === 0) {
        this.$message.warning('请先上传题库文件');
        return;
      }

      this.isProcessing = true;
      this.ocrProgress = 0;

      try {
        const recognizedText = await recognizeQuestionFromImage(imageFile);
        this.searchQuery = recognizedText; // 填充到搜索框
        this.searchQuestion(recognizedText);
      } catch (err) {
        console.error('OCR识别失败:', err);
        this.$message.error('图片识别失败，请尝试清晰的图片');
      } finally {
        this.isProcessing = false;
      }
    },

    searchQuestion(query) {
      if (!query.trim()) return;

      const result = searchQuestion(this.questions, query);
      if (result) {
        this.currentAnswer = result.answer;
        this.$message.success('找到匹配答案');
      } else {
        this.currentAnswer = '未找到匹配答案';
        this.$message.warning('未找到匹配答案');
      }
    },
    // 搜索习题
    handleSearch() {
      if (!this.searchQuery.trim()) return;

      this.hasContent = true;
      this.showSolution = true;

      // 模拟搜索结果
      this.currentExercise = {
        id: 'SE-' + Date.now(),
        title: `搜索结果: ${this.searchQuery}`,
        category: '软件工程',
        difficulty: '中等',
        question: '在软件测试中，什么是边界值分析？请举例说明边界值分析在测试输入字段年龄（有效范围18-60岁）时的应用。',
        solution: `
          <div class="solution-step">
            边界值分析是一种黑盒测试技术，它关注输入域的边界而不是中心。边界值分析基于这样的观察：错误更可能发生在输入域的边界附近而不是中心。
          </div>
          <div class="solution-step">
            对于年龄字段（有效范围18-60岁），边界值分析会测试以下值：
            <ul style="margin-top: 10px; margin-left: 20px;">
              <li>刚好低于最小值：17岁（无效）</li>
              <li>最小值：18岁（有效）</li>
              <li>刚好高于最小值：19岁（有效）</li>
              <li>正常值：30岁（有效）</li>
              <li>刚好低于最大值：59岁（有效）</li>
              <li>最大值：60岁（有效）</li>
              <li>刚好高于最大值：61岁（无效）</li>
            </ul>
          </div>
          <div class="solution-step">
            这种测试方法能有效发现边界条件相关的错误，如错误的比较运算符（如使用了>而不是>=）。
          </div>
        `,
        image: null
      };
    },

    // 触发文件选择
    triggerFileInput() {
      this.$refs.fileInput.click();
    },

    // 处理拖拽进入
    handleDragOver() {
      this.uploadIconClass = 'fas fa-file-upload';
      this.uploadIconColor = '#4a6bff';
      this.uploadText = '松开上传图片';
    },

    // 处理拖拽离开
    handleDragLeave() {
      this.uploadIconClass = 'fas fa-cloud-upload-alt';
      this.uploadIconColor = '#4a6bff';
      this.uploadText = '点击或拖拽图片到此处上传';
    },

    // 处理拖放
    handleDrop(e) {
      this.handleDragLeave();
      if (e.dataTransfer.files.length) {
        this.handleFileUpload(e.dataTransfer.files[0]);
      }
    },

    // 处理文件选择
    handleFileChange(e) {
      if (e.target.files.length) {
        this.handleFileUpload(e.target.files[0]);
      }
    },

    // 处理文件上传
    handleFileUpload(file) {
      // 显示上传成功反馈
      this.uploadIconClass = 'fas fa-check-circle';
      this.uploadIconColor = '#10b981';
      this.uploadText = `已选择: ${file.name}`;

      // 3秒后恢复原始状态
      setTimeout(() => {
        this.uploadIconClass = 'fas fa-cloud-upload-alt';
        this.uploadIconColor = '#4a6bff';
        this.uploadText = '点击或拖拽图片到此处上传';
      }, 3000);

      // 模拟上传后的结果
      this.hasContent = true;
      this.showSolution = true;

      // 预览图片
      const reader = new FileReader();
      reader.onload = (e) => {
        this.currentExercise = {
          id: 'IMG_' + Date.now(),
          title: '上传习题解析',
          category: '图片习题',
          difficulty: '中等',
          question: '这是您上传的习题，系统已自动分析并识别题目内容。边界值分析是软件测试中的关键技术，用于检测边界条件错误。',
          solution: `
            <div class="solution-step">
              根据您上传的图片，系统识别出这是一道关于软件测试边界值分析的题目。
            </div>
            <div class="solution-step">
              边界值分析是一种黑盒测试技术，专注于输入域的边界值。在年龄字段测试案例中（有效范围18-60岁），我们需要测试以下关键点：
              <ul style="margin-top: 10px; margin-left: 20px;">
                <li>17岁（刚好低于最小值）</li>
                <li>18岁（最小值）</li>
                <li>19岁（刚好高于最小值）</li>
                <li>59岁（刚好低于最大值）</li>
                <li>60岁（最大值）</li>
                <li>61岁（刚好高于最大值）</li>
              </ul>
            </div>
            <div class="solution-step">
              这种测试方法能有效发现边界条件相关的错误，如错误的比较运算符（如使用了>而不是>=）。
            </div>
          `,
          image: e.target.result
        };
      };
      reader.readAsDataURL(file);
    },

    // 选择相关习题
    selectRelatedExercise(exercise) {
      this.hasContent = true;
      this.showSolution = true;
      this.currentExercise = {
        ...this.currentExercise,
        title: exercise.title,
        category: exercise.category,
        difficulty: exercise.difficulty,
        question: `这是关于${exercise.title}的问题描述。软件测试是确保软件质量的关键过程，边界值分析和等价类划分是两种常用的测试技术。`,
        solution: `
          <div class="solution-step">
            对于题目"${exercise.title}"，以下是详细解析：
          </div>
          <div class="solution-step">
            边界值分析(Boundary Value Analysis)和等价类划分(Equivalence Partitioning)是两种互补的黑盒测试技术。
          </div>
          <div class="solution-step">
            边界值分析关注输入域的边界值，而等价类划分则将输入域划分为若干等价类，从每个等价类中选取代表性值进行测试。
          </div>
          <div class="solution-step">
            在实际测试中，通常结合使用这两种技术，例如在测试年龄字段时：
            <ul style="margin-top: 10px; margin-left: 20px;">
              <li>边界值：17, 18, 19, 59, 60, 61</li>
              <li>等价类：有效类(18-60)、无效类(小于18)、无效类(大于60)</li>
            </ul>
          </div>
        `,
        image: null
      };
    }
  }
}
</script>

<style scoped>
:root {
  --primary-color: #4a6bff;
  --secondary-color: #3a5beb;
  --success-color: #10b981;
  --dark-color: #1e293b;
  --gray-color: #e2e8f0;
  --light-color: #f1f5f9;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

body {
  background: linear-gradient(135deg, #f8fafc 0%, #eef2f6 100%);
  color: var(--dark-color);
  line-height: 1.6;
  min-height: 100vh;
  padding: 20px;
}

.app-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  height: calc(100vh - 60px);
  overflow-y: auto;
  scrollbar-width: none; /* Firefox 隐藏滚动条 */
  -ms-overflow-style: none; /* IE/Edge 隐藏滚动条 */
}

.header {
  text-align: center;
  margin-bottom: 30px;
  padding: 20px;
}

.header h1 {
  font-size: 2.5rem;
  color: var(--dark-color);
  margin-bottom: 10px;
  background: linear-gradient(90deg, var(--primary-color), #6a11cb);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  display: inline-block;
}

.header p {
  font-size: 1.1rem;
  color: #64748b;
  max-width: 600px;
  margin: 0 auto;
}

/* 搜索区域 */
.search-section {
  background-color: white;
  border-radius: 16px;
  padding: 25px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
  margin-bottom: 30px;
  transition: transform 0.3s, box-shadow 0.3s;
}

.search-section:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.1);
}

.search-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--dark-color);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-title i {
  color: var(--primary-color);
}

.search-box {
  display: flex;
  gap: 15px;
  margin-bottom: 20px;
}

.search-input {
  flex: 1;
  padding: 15px 20px;
  border: 1px solid var(--gray-color);
  border-radius: 12px;
  font-size: 1rem;
  transition: all 0.3s;
  background-color: #f8fafc;
}

.search-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 4px rgba(74, 107, 255, 0.2);
}

.search-btn {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: white;
  border: none;
  border-radius: 12px;
  padding: 0 30px;
  font-size: 1.05rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 10px rgba(74, 107, 255, 0.3);
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 15px rgba(74, 107, 255, 0.4);
}

.search-btn:active {
  transform: translateY(0);
}

.upload-section {
  border: 2px dashed var(--gray-color);
  border-radius: 12px;
  padding: 35px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 10px;
  background-color: #f8fafc;
}

.upload-section:hover {
  border-color: var(--primary-color);
  background-color: rgba(74, 107, 255, 0.03);
}

.upload-icon {
  font-size: 48px;
  color: var(--primary-color);
  margin-bottom: 15px;
  transition: all 0.3s;
}

.upload-text {
  font-size: 1.1rem;
  color: var(--dark-color);
  margin-bottom: 10px;
  font-weight: 500;
}

.upload-hint {
  font-size: 0.95rem;
  color: #64748b;
}

/* 内容区域 */
.content-section {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.exercise-detail {
  background-color: white;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s;
}

.exercise-detail:hover {
  transform: translateY(-3px);
}

.exercise-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--gray-color);
}

.exercise-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--dark-color);
  display: flex;
  align-items: center;
  gap: 10px;
}

.exercise-title i {
  color: var(--primary-color);
}

.exercise-meta {
  display: flex;
  gap: 15px;
  align-items: center;
  flex-wrap: wrap;
}

.exercise-tag {
  background-color: var(--light-color);
  color: var(--dark-color);
  padding: 8px 16px;
  border-radius: 50px;
  font-size: 0.9rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
}

.toggle-btn {
  background: white;
  border: 2px solid var(--primary-color);
  color: var(--primary-color);
  padding: 8px 16px;
  border-radius: 50px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
}

.toggle-btn:hover {
  background-color: rgba(74, 107, 255, 0.1);
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
}

.exercise-content {
  margin-bottom: 25px;
}

.question-text {
  font-size: 1.15rem;
  margin-bottom: 25px;
  line-height: 1.8;
  color: #334155;
  background: linear-gradient(transparent 90%, rgba(74, 107, 255, 0.1) 90%);
  padding: 5px 0;
}

.question-image {
  max-width: 100%;
  border-radius: 12px;
  margin-bottom: 25px;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.08);
  border: 1px solid #e2e8f0;
}

.solution-section {
  background: linear-gradient(to bottom right, #f8fafc, #f0f7ff);
  border-radius: 12px;
  padding: 25px;
  margin-top: 25px;
  border-left: 4px solid var(--primary-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.solution-title {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--primary-color);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.solution-content {
  line-height: 1.8;
  color: #334155;
}

.solution-step {
  margin-bottom: 20px;
  padding-left: 25px;
  position: relative;
  font-size: 1.05rem;
}

.solution-step:before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  width: 12px;
  height: 12px;
  background-color: var(--primary-color);
  border-radius: 50%;
}

/* 更多习题推荐 - 横向三列布局 */
.related-exercises {
  background-color: white;
  border-radius: 12px;
  padding: 25px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  height: fit-content;
  margin-top: 30px;
}

.related-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--dark-color);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.related-title i {
  color: var(--primary-color);
}

.related-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 25px;
}

.related-item {
  background: linear-gradient(to bottom, white 0%, #f8fafc 100%);
  border-radius: 12px;
  padding: 25px;
  transition: all 0.3s;
  cursor: pointer;
  border: 1px solid var(--gray-color);
  display: flex;
  flex-direction: column;
  gap: 15px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.03);
  position: relative;
  overflow: hidden;
}

.related-item:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 25px rgba(0, 0, 0, 0.1);
  border-color: var(--primary-color);
}

.related-item:before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 5px;
  height: 100%;
  background: linear-gradient(to bottom, var(--primary-color), var(--secondary-color));
}

.related-item-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 5px;
  color: var(--dark-color);
  padding-left: 10px;
  border-left: 3px solid var(--primary-color);
}

.related-item-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding-left: 13px;
}

/* 空内容提示样式 */
.empty-content {
  background-color: white;
  border-radius: 16px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.empty-icon {
  font-size: 70px;
  color: var(--primary-color);
  margin-bottom: 25px;
  opacity: 0.8;
}

.empty-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--dark-color);
  margin-bottom: 15px;
}

.empty-hint {
  font-size: 1.1rem;
  color: #64748b;
  max-width: 500px;
  line-height: 1.7;
}

.action-btn {
  margin-top: 25px;
  padding: 12px 30px;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: white;
  border: none;
  border-radius: 50px;
  font-size: 1.05rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(74, 107, 255, 0.3);
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.action-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 7px 20px rgba(74, 107, 255, 0.4);
}

/* 响应式设计 */
@media (max-width: 992px) {
  .related-list {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .search-box {
    flex-direction: column;
  }

  .search-btn {
    padding: 15px;
    justify-content: center;
  }

  .exercise-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }

  .exercise-meta {
    width: 100%;
    justify-content: flex-start;
  }

  .related-list {
    grid-template-columns: 1fr;
  }

  .empty-content {
    padding: 40px 20px;
  }

  .header h1 {
    font-size: 2rem;
  }
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.5s;
}
.fade-enter, .fade-leave-to {
  opacity: 0;
}

.footer {
  text-align: center;
  margin-top: 50px;
  padding: 20px;
  color: #64748b;
  font-size: 0.9rem;
}
</style>