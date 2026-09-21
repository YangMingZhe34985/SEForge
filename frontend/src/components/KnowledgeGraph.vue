<template>
  <div class="knowledge-container">
    <!-- 搜索区域 -->
    <div class="search-section">
      <h2 class="search-title">
        <i class="fas fa-project-diagram"></i>
        知识图谱探索
      </h2>
      <div class="search-box">
        <input
            type="text"
            class="search-input"
            placeholder="输入关键词探索知识图谱..."
            v-model="searchQuery"
            @keyup.enter="handleSearch"
        >
        <button class="search-btn" @click="handleSearch">探索</button>
      </div>

      <!-- 热词推荐 -->
      <div class="hot-words-section">
        <div class="hot-words-header">
          <div class="hot-words-title">
            <i class="fas fa-fire"></i>
            热门搜索词
          </div>
          <button class="refresh-btn" @click="refreshHotWords">
            <i class="fas fa-sync-alt" :class="{ 'fa-spin': isRefreshing }"></i>
            换一换
          </button>
        </div>
        <div class="hot-words-list">
          <div
              class="hot-word"
              v-for="(word, index) in hotWords"
              :key="index"
              @click="selectHotWord(word)"
          >
            {{ word }}
          </div>
        </div>
      </div>
    </div>

    <!-- 知识图谱展示区 -->
    <div class="graph-section">
      <div class="graph-header">
        <h3 class="graph-title">
          <i class="fas fa-network-wired"></i>
          知识图谱可视化
        </h3>
        <div class="graph-tools">
          <button class="graph-tool-btn" @click="handleFullscreen">
            <i class="fas fa-expand"></i>
            全屏
          </button>
          <button class="graph-tool-btn" @click="handleExport">
            <i class="fas fa-download"></i>
            导出
          </button>
          <button class="graph-tool-btn" @click="toggleSettingsPanel">
            <i class="fas fa-cog"></i>
            设置
          </button>
        </div>
      </div>

      <div class="graph-container" ref="graphContainer">
        <!-- 这里将使用JavaScript渲染知识图谱 -->
        <div class="graph-placeholder" v-if="!currentKeyword">
          <div class="graph-placeholder-icon">
            <i class="fas fa-project-diagram"></i>
          </div>
          <div class="graph-placeholder-text">
            输入关键词或点击热门搜索词开始探索知识图谱
          </div>
          <button class="graph-placeholder-btn" @click="randomExplore">
            <i class="fas fa-random"></i>
            随机探索
          </button>
        </div>
        <div v-else class="graph-content">
          <!-- 这里可以放置实际的知识图谱可视化内容 -->
          <div class="simulated-graph">
            <div class="node center-node">{{ currentKeyword }}</div>
            <div
                v-for="(relation, index) in relatedNodes"
                :key="index"
                class="node related-node"
                :style="getNodePosition(index)"
            >
              {{ relation }}
            </div>
          </div>
        </div>
      </div>

      <!-- 节点信息面板 -->
      <div class="node-info-panel" v-if="selectedNode">
        <div class="node-info-header">
          <div class="node-info-title">节点信息: {{ selectedNode ? selectedNode.name : '' }}</div>
          <button class="close-info-btn" @click="closeNodeInfo">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="node-info-content">
          <div class="node-info-property">
            <span class="node-info-label">名称:</span>
            <span class="node-info-value">{{ selectedNode ? selectedNode.name : '' }}</span>
          </div>
          <div class="node-info-property">
            <span class="node-info-label">类型:</span>
            <span class="node-info-value">
              {{ selectedNode ? (selectedNode.type || (selectedNode.categories && selectedNode.categories.length > 0 ? selectedNode.categories[0] : '未知')) : '' }}
            </span>
          </div>
          <div class="node-info-property">
            <span class="node-info-label">描述:</span>
            <span class="node-info-value">{{ selectedNode ? selectedNode.definition || '暂无详细描述信息' : '' }}</span>
          </div>
          <div class="node-info-relations">
            <div class="node-info-label">相关概念:</div>
            <div
                class="relation-item"
                v-for="(relation, index) in relatedNodes"
                :key="index"
            >
              {{ relation }}
            </div>
          </div>
        </div>
      </div>

      <!-- **新增：设置面板** -->
      <div class="settings-panel" v-if="settingsVisible">
          <div class="settings-header">
              <div class="settings-title">图谱设置</div>
              <button class="close-settings-btn" @click="toggleSettingsPanel">
                  <i class="fas fa-times"></i>
              </button>
          </div>
          <div class="settings-content">
              <div class="setting-item">
                  <label for="maxDepth">探索深度:</label>
                  <input type="number" id="maxDepth" v-model.number="currentMaxDepth" min="1" @change="applySettings">
              </div>
               <div class="setting-item">
                  <label for="repulsion">节点斥力:</label>
                  <input type="range" id="repulsion" v-model.number="currentRepulsion" min="100" max="5000" step="100" @input="updateGraphOptions">
                  <span>{{ currentRepulsion }}</span>
              </div>
               <div class="setting-item">
                  <label for="edgeLength">理想边长:</label>
                  <input type="range" id="edgeLength" v-model.number="currentEdgeLength" min="10" max="500" step="10" @input="updateGraphOptions">
                   <span>{{ currentEdgeLength }}</span>
              </div>
               <div class="setting-item">
                  <label for="showEdgeLabels">显示边标签:</label>
                   <input type="checkbox" id="showEdgeLabels" v-model="showEdgeLabels" @change="updateGraphOptions">
              </div>
              <!-- 可以添加更多设置项 -->
          </div>
      </div>
      <!-- **设置面板结束** -->

    </div>
  </div>
</template>

<script>
import axios from 'axios';
import * as echarts from 'echarts';

export default {
  name: 'KnowledgeGraph',
  data() {
    return {
      searchQuery: '',
      currentKeyword: '',
      showNodeInfo: false,
      isRefreshing: false,
      hotWords: [
        "软件工程", "设计模式", "UML", "敏捷开发", "测试驱动开发",
        "面向对象", "MVC架构", "微服务"
      ],
      hotWordsPool: [
        "软件工程", "设计模式", "UML", "敏捷开发", "测试驱动开发",
        "面向对象", "MVC架构", "微服务", "DevOps", "持续集成",
        "代码重构", "设计原则", "软件架构", "设计模式", "工厂模式",
        "单例模式", "观察者模式", "策略模式", "装饰器模式", "适配器模式",
        "软件测试", "单元测试", "集成测试", "系统测试", "验收测试",
        "黑盒测试", "白盒测试", "灰盒测试", "测试覆盖率", "代码审查"
      ],
      relatedNodes: [],
      nodeDescription: '',
      graphNodes: [],
      graphEdges: [],
      maxDepth: 2,
      myChart: null,
      selectedNode: null,
      settingsVisible: false,
      currentMaxDepth: 2,
      currentRepulsion: 1000,
      currentEdgeLength: 100,
      showEdgeLabels: true,
      appliedMaxDepth: 2,
      appliedRepulsion: 1000,
      appliedEdgeLength: 100,
      appliedShowEdgeLabels: true,
    }
  },
  methods: {
    // 处理搜索
    handleSearch() {
      if (this.searchQuery.trim()) {
        this.currentKeyword = this.searchQuery.trim();
        console.log('搜索知识图谱:', this.currentKeyword);
        this.graphNodes = [];
        this.graphEdges = [];
        this.relatedNodes = [];
        this.nodeDescription = '';
        this.selectedNode = null;
        this.showNodeInfo = false;
        this.settingsVisible = false;

        // 销毁旧的 ECharts 实例
        if (this.myChart) {
            this.myChart.dispose();
            this.myChart = null;
        }

        this.appliedMaxDepth = this.currentMaxDepth;
        this.appliedRepulsion = this.currentRepulsion;
        this.appliedEdgeLength = this.currentEdgeLength;
        this.appliedShowEdgeLabels = this.showEdgeLabels;

        this.fetchKnowledgeGraph(this.searchQuery.trim());
      } else {
        // 如果搜索框为空，清空所有相关信息和图谱
        this.currentKeyword = '';
        this.showNodeInfo = false;
        this.relatedNodes = [];
        this.nodeDescription = '';
        this.graphNodes = [];
        this.graphEdges = [];
        this.selectedNode = null;
        this.settingsVisible = false;

        // 销毁旧的 ECharts 实例
        if (this.myChart) {
            this.myChart.dispose();
            this.myChart = null;
        }
      }
    },

    // 选择热门词
    selectHotWord(word) {
      this.searchQuery = word;
      this.handleSearch();
    },

    // 刷新热门词
    refreshHotWords() {
      this.isRefreshing = true;

      // 随机选择8个不重复的热词
      const shuffled = [...this.hotWordsPool].sort(() => 0.5 - Math.random());
      this.hotWords = shuffled.slice(0, 8);

      setTimeout(() => {
        this.isRefreshing = false;
      }, 500);
    },

    // 随机探索
    randomExplore() {
      const randomIndex = Math.floor(Math.random() * this.hotWordsPool.length);
      this.searchQuery = this.hotWordsPool[randomIndex];
      this.handleSearch();
    },

    // 关闭节点信息
    closeNodeInfo() {
      this.showNodeInfo = false;
      this.selectedNode = null;
    },

    // 获取节点位置（模拟布局）
    getNodePosition(index) {
      const angle = (index * (2 * Math.PI / this.relatedNodes.length)) - Math.PI/2;
      const radius = 150;
      return {
        left: `calc(50% + ${radius * Math.cos(angle)}px)`,
        top: `calc(50% + ${radius * Math.sin(angle)}px)`
      };
    },

    // 为关键词生成描述
    getDescriptionForKeyword(keyword) {
      // 直接返回从 API 获取的描述，现在取决于 selectedNode
      // 如果 selectedNode 存在，使用 selectedNode 的 definition
      // 否则，如果 nodeDescription （来自根节点）存在，使用它
      // 再否则，显示默认信息
      return this.selectedNode ? this.selectedNode.definition || "暂无详细描述信息" : this.nodeDescription || "暂无详细描述信息";
    },

    // 新增方法：根据节点ID查找其直接关联节点的名称
    findRelatedNodeNames(nodeId) {
        const relatedNames = [];
        // 遍历边列表，找到以当前节点为 source 的边
        const directRelationships = this.graphEdges.filter(edge => edge.source === nodeId);

        directRelationships.forEach(edge => {
            // 找到目标节点的详细信息
            const targetNode = this.graphNodes.find(node => node.id === edge.target);
            if (targetNode) {
                relatedNames.push(targetNode.name);
            }
        });
        return relatedNames;
    },

    // 新增方法：处理节点点击事件
    handleNodeClick(params) {
        if (params.dataType === 'node') {
            const clickedNodeData = params.data; // 这是 ECharts 内部处理过的节点数据，包含原始数据的引用 originalData

            // 找到原始的完整节点数据
            const fullNodeData = clickedNodeData.originalData;
            if (fullNodeData) {
                this.selectedNode = fullNodeData; // 更新 selectedNode
                // this.nodeDescription = fullNodeData.definition || "暂无详细描述信息"; // 描述现在直接从 selectedNode 获取，这里不需要单独更新

                // 查找并更新相关概念
                this.relatedNodes = this.findRelatedNodeNames(fullNodeData.id);

                this.showNodeInfo = true; // 显示节点信息面板
                console.log('点击节点:', this.selectedNode.name, '相关概念:', this.relatedNodes);
            }
        }
    },

    // 新增方法：处理导出事件
    handleExport() {
        if (this.myChart) {
            try {
                // 获取图表的 base64 数据 URL
                const dataURL = this.myChart.getDataURL({
                    // excludeComponents: ['toolbox'], // 如果toolbox不需要导出可以exclude
                    // type: 'png', // 导出图片类型，默认为 png
                    // pixelRatio: 2, // 导出的图片分辨率，可以适当调高
                    // backgroundColor: '#fff' // 导出的图片背景颜色
                });

                // 创建一个临时的下载链接
                const link = document.createElement('a');
                link.download = `知识图谱_${this.currentKeyword || 'export'}.png`; // 设置下载的文件名
                link.href = dataURL;

                // 触发下载
                document.body.appendChild(link);
                link.click();

                // 移除临时链接
                document.body.removeChild(link);

                console.log('知识图谱已导出为图片');
                 this.$message.success('知识图谱已导出');

            } catch (error) {
                console.error('导出知识图谱失败:', error);
                 this.$message.error('导出知识图谱失败');
            }
        } else {
            console.warn('ECharts 实例未初始化，无法导出');
             this.$message.warning('知识图谱未加载，无法导出');
        }
    },

    // 新增方法：处理全屏事件
    handleFullscreen() {
        const element = this.$refs.graphContainer;

        if (element) {
            // 检查是否支持全屏API
            if (element.requestFullscreen) {
                element.requestFullscreen();
            } else if (element.webkitRequestFullscreen) { /* Safari */
                element.webkitRequestFullscreen();
            } else if (element.msRequestFullscreen) { /* IE11 */
                element.msRequestFullscreen();
            } else {
                 this.$message.warning('您的浏览器不支持全屏模式');
            }
        }
    },

    // 新增方法：处理全屏状态变化事件，并调整 ECharts 大小
    handleFullscreenChange() {
        // 使用 $nextTick 确保 DOM 状态更新后再调整 ECharts 大小
        this.$nextTick(() => {
            if (this.myChart) {
                // 判断当前是否处于全屏模式
                const isFullscreen = document.fullscreenElement || document.webkitFullscreenElement || document.msFullscreenElement;

                if (isFullscreen) {
                    console.log('进入全屏模式');
                     // 如果进入全屏，可能需要重新计算容器大小并resize ECharts
                     // ECharts resize 方法会根据容器的当前大小自动调整
                    this.myChart.resize();
                } else {
                    console.log('退出全屏模式');
                     // 退出全屏后，也要重新调整 ECharts 大小
                     this.myChart.resize();
                }
            }
        });
    },

    // 新增方法：处理 API 响应并构建节点和边列表
    buildGraphData(node, currentDepth, nodesSet, edgesSet) {
        if (currentDepth > this.appliedMaxDepth) {
            return;
        }

        // 添加当前节点
        if (!nodesSet.has(node.id)) {
            nodesSet.add(node.id);
            this.graphNodes.push({
                id: node.id,
                name: node.name,
                definition: node.definition,
                categories: node.categories,
                type: node.type,
                symbolSize: currentDepth === 0 ? 40 : 20,
                category: node.type || (node.categories && node.categories.length > 0 ? node.categories[0] : '未知类型'), // 使用 type 或 categories[0] 作为 category
                // 存储原始数据，方便 tooltip 和点击事件使用
                originalData: node
            });
        }

        // 添加关系和目标节点
        if (node.relationships && node.relationships.length > 0) {
            node.relationships.forEach(relationship => {
                if (relationship.targetNode) {
                    // 添加边
                    const edgeId = `${relationship.sourceNodeId}-${relationship.targetNodeId}-${relationship.type || ''}`; // 使用更唯一的边ID
                    if (!edgesSet.has(edgeId)) {
                         edgesSet.add(edgeId);
                         this.graphEdges.push({
                             source: relationship.sourceNodeId,
                             target: relationship.targetId || relationship.targetNode.id, // 使用 targetId 或 targetNode.id
                             value: relationship.type || '关联' // 使用关系类型作为边的标签或值
                              // 存储原始数据
                             // originalData: relationship // 如果需要边的详细信息
                         });
                         // console.log(`添加边: ${node.name} -> ${relationship.targetNode.name} (${relationship.type || '关联'})`);
                    }

                    // 递归处理目标节点
                    this.buildGraphData(relationship.targetNode, currentDepth + 1, nodesSet, edgesSet);
                }
            });
        }
    },

    // 实际API调用方法
    fetchKnowledgeGraph(keyword) {
      console.log('调用知识图谱API:', keyword);

      // 清空之前的数据和选中状态
      this.graphNodes = [];
      this.graphEdges = [];
      this.relatedNodes = [];
      this.nodeDescription = '';
      this.selectedNode = null;
      this.showNodeInfo = false;
      this.settingsVisible = false;


      axios.get(`http://localhost:8080/api/knowledge/nodes`, {
        params: {
          name: keyword
        }
      })
      .then(response => {
        console.log('获取知识图谱成功:', response.data);
        // 处理返回的知识图谱数据
        if (response.data && response.data.success && response.data.data) {
          const rootNode = response.data.data;

          // 使用 buildGraphData 方法构建多层级图谱数据
          const nodesSet = new Set();
          const edgesSet = new Set();
          this.buildGraphData(rootNode, 0, nodesSet, edgesSet);

           // 数据加载并构建完成后，不自动选中节点，不显示面板

          console.log('提取到的所有节点:', this.graphNodes);
          console.log('提取到的所有边:', this.graphEdges);


          // 调用渲染方法
          this.$nextTick(() => { // 确保DOM更新后再渲染
             this.renderKnowledgeGraph();
             // ECharts 渲染完成后，如果之前有选中的节点，可以尝试在这里重新高亮它 (复杂，暂不实现)
          });


        } else {
          console.warn('获取知识图谱成功但数据结构不符合预期或未找到相关节点:', response.data);
          this.$message.warning('未找到相关知识图谱数据');
          this.graphNodes = [];
          this.graphEdges = [];
          this.relatedNodes = [];
          this.nodeDescription = "暂无详细描述信息";
          this.selectedNode = null;
          this.showNodeInfo = false;
          // 销毁 ECharts 实例
          if (this.myChart) {
            this.myChart.dispose();
            this.myChart = null;
          }
        }
      })
      .catch(error => {
        console.error('获取知识图谱失败:', error);
        // 根据错误类型给出提示
        if (error.response) {
          console.error('响应数据:', error.response.data);
          console.error('响应状态码:', error.response.status);
          console.error('响应头:', error.response.headers);
          this.$message.error(`获取知识图谱失败: ${error.response.status} - ${error.response.data.message || '服务器错误'}`);
        } else if (error.request) {
          console.error('请求:', error.request);
          this.$message.error('获取知识图谱失败: 未收到服务器响应，请检查后端服务是否运行');
        } else {
          console.error('错误信息:', error.message);
          this.$message.error(`获取知识图谱失败: ${error.message}`);
        }
        this.graphNodes = [];
        this.graphEdges = [];
        this.relatedNodes = [];
        this.nodeDescription = "暂无详细描述信息";
        this.selectedNode = null;
        this.showNodeInfo = false;
         // 销毁 ECharts 实例
        if (this.myChart) {
            this.myChart.dispose();
            this.myChart = null;
        }
      });
    },

    // 使用 ECharts 渲染图谱
    renderKnowledgeGraph() {
      // 基于准备好的dom，初始化echarts实例
      const chartDom = this.$refs.graphContainer;
      if (!chartDom) {
          console.error('图谱容器 DOM 元素未找到');
          return;
      }

      // 如果实例不存在，才初始化
      if (!this.myChart) {
         this.myChart = echarts.init(chartDom);
         // 只有在初始化时才添加事件监听，避免重复添加
         this.myChart.on('click', this.handleNodeClick);
      }


      // 动态生成类别列表
      const categories = [...new Set(this.graphNodes.map(node => node.category))].filter(cat => cat);
      const categoryData = categories.map(cat => ({ name: cat })); // ECharts categories 需要 { name: '...' } 格式

      // 配置项和数据
      const option = {
        backgroundColor: '#fff',
        title: {
          text: '知识图谱',
          top: 'bottom',
          left: 'right'
        },
        tooltip: { // 鼠标悬停提示框
            formatter: function (params) {
                if (params.dataType === 'node') {
                    // 显示节点信息
                    const node = params.data.originalData; // 使用存储的原始数据
                    let tooltip = `<strong>${node.name}</strong><br/>`;
                    // 使用 type 或 categories[0] 显示类型
                    const type = node.type || (node.categories && node.categories.length > 0 ? node.categories[0] : '未知');
                    tooltip += `类型: ${type}<br/>`;
                    tooltip += `定义: ${node.definition || '暂无定义'}`;
                     // 可以根据需要添加更多节点属性
                    return tooltip;
                } else if (params.dataType === 'edge') {
                    // 显示边信息
                    const edge = params.data;
                     // 找到对应的源节点和目标节点
                    const sourceNode = this.graphNodes.find(n => n.id === edge.source);
                    const targetNode = this.graphNodes.find(n => n.id === edge.target);
                    if (sourceNode && targetNode) {
                         return `<strong>${sourceNode.name}</strong> - ${edge.value || '关联'} - <strong>${targetNode.name}</strong>`;
                    }
                    return '';
                }
                return '';
            }.bind(this) // 绑定 this，以便在 formatter 中访问组件的 this
        },
        legend: [{ // 图例，用于区分不同类别的节点
             // 使用动态生成的类别列表
            data: categories
        }],
        series: [
          {
            name: '知识图谱',
            type: 'graph',
            layout: 'force', // 使用力引导布局
            data: this.graphNodes.map(node => ({ // 转换节点数据格式
                id: node.id,
                name: node.name,
                symbolSize: node.symbolSize,
                category: node.category,
                // 存储原始数据以便 tooltip 和点击事件使用
                originalData: node
            })),
            links: this.graphEdges.map(edge => ({ // 转换边数据格式
                source: edge.source,
                target: edge.target,
                value: edge.value, // 边的值或标签
                 // 可以在这里添加其他视觉映射的属性
                // originalData: edge // 如果需要边的详细信息
            })),
            categories: categoryData, // 使用动态生成的类别数据
            roam: true, // 开启缩放和平移
            label: { // 节点标签
              show: true,
              position: 'right',
              formatter: '{b}' // 显示节点名称
            },
            labelLayout: { // 避免标签重叠
                hideOverlap: true // 节点标签可以隐藏重叠
            },
            edgeLabel: { // 边标签
                 // 根据已应用的 showEdgeLabels 控制显示
                 show: this.appliedShowEdgeLabels,
                 formatter: '{c}', // 显示边的值 (关系类型)
                 position: 'middle',
                 labelLayout: {
                     hideOverlap: false // 边标签不隐藏重叠
                 }
            },
            force: { // 力引导布局配置
              repulsion: this.appliedRepulsion, // 使用已应用的斥力值
              edgeLength: this.appliedEdgeLength, // 使用已应用的边长值
            },
            emphasis: { // 高亮样式
              focus: 'adjacency', // 高亮邻接节点和边
              label: {
                 position: 'right',
                 show: true
              }
            },
            lineStyle: { // 边的样式
              curveness: 0.3 // 边的弯曲度
            }
          }
        ]
      };


      // 使用刚指定的配置项和数据显示图表。
      // 注意：如果 myChart 已经存在，setOption 会合并配置，力布局会重新计算
      this.myChart.setOption(option); // 移除 notMerge: true，让 ECharts 智能合并配置

      // 移除点击事件监听逻辑，改到初始化时添加一次
      // this.myChart.off('click'); // 先移除旧的监听
      // this.myChart.on('click', this.handleNodeClick);
    },

    // **新增：切换设置面板显示状态**
    toggleSettingsPanel() {
        this.settingsVisible = !this.settingsVisible;
         // 当打开设置面板时，将当前已应用的设置值同步到面板的 current... 值，以便编辑
        if (this.settingsVisible) {
            this.currentMaxDepth = this.appliedMaxDepth;
            this.currentRepulsion = this.appliedRepulsion;
            this.currentEdgeLength = this.appliedEdgeLength;
            this.showEdgeLabels = this.appliedShowEdgeLabels;
        } else {
             // 当关闭面板时，如果只是布局/显示设置改变，且没有通过 maxDepth 改变触发 reload，
             // 需要将面板的 current... 值同步到 applied... 值，以便 renderKnowledgeGraph 使用
             // 但更明确的做法是只在 applySettings 中同步 applied... 值
        }
    },

    // **修改：应用设置**
    // 这个方法现在只负责根据 maxDepth 是否改变来决定是否重新获取数据
    applySettings() {
         console.log('应用设置...');
         this.settingsVisible = false; // 应用设置后隐藏面板

        // 判断探索深度是否改变
        if (this.currentMaxDepth !== this.appliedMaxDepth) {
            // 如果探索深度改变，需要重新获取数据
            console.log(`探索深度从 ${this.appliedMaxDepth} 变为 ${this.currentMaxDepth}，重新获取数据。`);
             // 清空当前图谱数据和选中状态
             this.graphNodes = [];
             this.graphEdges = [];
             this.relatedNodes = [];
             this.nodeDescription = '';
             this.selectedNode = null;
             this.showNodeInfo = false;

             // 销毁旧的 ECharts 实例
             if (this.myChart) {
                this.myChart.dispose();
                this.myChart = null;
             }

            // 更新已应用的深度设置
            this.appliedMaxDepth = this.currentMaxDepth;
            // 其他设置会随着 fetchKnowledgeGraph -> buildGraphData -> renderKnowledgeGraph 的流程被应用


             // 重新发起 API 请求
            if (this.currentKeyword) {
                 this.fetchKnowledgeGraph(this.currentKeyword);
            } else {
                 this.$message.info('请先搜索关键词');
            }

        } else {
            // 如果探索深度没有改变，其他设置的改变已经在 updateGraphOptions 中实时预览了
            // 这里只需要同步一下 applied... 值，表示这些设置是"最终确认"的
             console.log('非深度设置改变，已通过预览更新。同步应用设置。');
             this.appliedRepulsion = this.currentRepulsion;
             this.appliedEdgeLength = this.currentEdgeLength;
             this.appliedShowEdgeLabels = this.showEdgeLabels;
             // 不需要再次调用 renderKnowledgeGraph()，因为它已经在 updateGraphOptions 中被调用了
        }
    },

    // **新增方法：实时更新图谱配置以预览效果**
    updateGraphOptions() {
         console.log('更新图谱选项预览...');
        if (this.myChart && this.graphNodes.length > 0) {
            // 获取当前配置项，只修改需要更新的部分
            const option = this.myChart.getOption();

            // 更新 force 布局参数
            if (option.series && option.series[0] && option.series[0].force) {
                option.series[0].force.repulsion = this.currentRepulsion;
                option.series[0].force.edgeLength = this.currentEdgeLength;
            }

            // 更新边标签显示状态
             if (option.series && option.series[0] && option.series[0].edgeLabel) {
                option.series[0].edgeLabel.show = this.showEdgeLabels;
             }

            // 应用更新后的配置项
            // 使用 setOption 会智能合并，只更新改变的部分
            this.myChart.setOption(option);

             // 注意：这里只更新了 ECharts 的配置，并没有修改 applied... 值
             // 应用 applied... 值会在 applySettings 或 search 时进行
        }
    },
  },
  mounted() {
    // 初始化时可以加载一些默认数据
    this.refreshHotWords();
     // 在 mounted 钩子中添加全屏状态变化的事件监听
     document.addEventListener('fullscreenchange', this.handleFullscreenChange);
     document.addEventListener('webkitfullscreenchange', this.handleFullscreenChange); // Safari
     document.addEventListener('msFullscreenChange', this.handleFullscreenChange); // IE11
  },
  beforeDestroy() { // 在组件销毁前销毁 ECharts 实例
    if (this.myChart) {
      this.myChart.off('click', this.handleNodeClick); // 移除点击事件监听
      this.myChart.dispose();
      this.myChart = null;
    }
    // 在组件销毁前移除事件监听
    document.removeEventListener('fullscreenchange', this.handleFullscreenChange);
    document.removeEventListener('webkitfullscreenchange', this.handleFullscreenChange);
    document.removeEventListener('msFullscreenChange', this.handleFullscreenChange);
  },
}
</script>

<style scoped>
/* 保持原有样式不变，添加一些额外的样式 */
.knowledge-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  height: calc(100vh - 60px);
  overflow-y: auto;
  scrollbar-width: none; /* Firefox 隐藏滚动条 */
  -ms-overflow-style: none; /* IE/Edge 隐藏滚动条 */
}

/* Chrome/Safari/Opera 隐藏滚动条 */
.knowledge-container::-webkit-scrollbar {
  display: none;
}

.search-section {
  background-color: white;
  border-radius: 12px;
  padding: 25px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  margin-bottom: 25px;
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
  padding: 12px 20px;
  border: 1px solid var(--gray-color);
  border-radius: 8px;
  font-size: 16px;
  transition: all 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(74, 107, 255, 0.2);
}

.search-btn {
  background-color: var(--primary-color);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 0 25px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.search-btn:hover {
  background-color: var(--secondary-color);
  transform: translateY(-2px);
}

.hot-words-section {
  margin-top: 20px;
}

.hot-words-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.hot-words-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--dark-color);
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-btn {
  background: none;
  border: none;
  color: var(--primary-color);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.3s;
}

.refresh-btn:hover {
  color: var(--secondary-color);
}

.hot-words-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hot-word {
  background-color: #f0f4ff;
  color: var(--primary-color);
  padding: 8px 15px;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.hot-word:hover {
  background-color: var(--primary-color);
  color: white;
  transform: translateY(-2px);
}

.graph-section {
  background-color: white;
  border-radius: 12px;
  padding: 25px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  margin-bottom: 25px;
  height: 600px; /* 确保有足够的高度 */
  position: relative;
  overflow: hidden;
}

.graph-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.graph-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--dark-color);
  display: flex;
  align-items: center;
  gap: 10px;
}

.graph-tools {
  display: flex;
  gap: 10px;
}

.graph-tool-btn {
  background-color: var(--light-color);
  color: var(--dark-color);
  border: none;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  gap: 5px;
}

.graph-tool-btn:hover {
  background-color: var(--gray-color);
}

.graph-container {
  width: 100%;
  height: calc(100% - 50px); /* 确保容器有高度 */
  border-radius: 8px;
  position: relative;
}

.graph-placeholder {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #a0aec0;
  z-index: 5; /* 确保 placeholder 在 ECharts 图层之上 */
  pointer-events: none; /* 允许点击穿透到下面的容器，但placeholder本身不响应点击 */
  background-color: white; /* 添加白色背景，防止 ECharts 初始化前的闪烁 */
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}
.graph-placeholder[v-if="true"] { /* 确保 v-if 生效 */
  display: flex;
}
.graph-placeholder[v-if="false"] {
    display: none;
}

.graph-placeholder-icon {
  font-size: 60px;
  margin-bottom: 15px;
  color: var(--gray-color);
}

.graph-placeholder-text {
  font-size: 16px;
  margin-bottom: 20px;
}

.graph-placeholder-btn {
  background-color: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  pointer-events: all; /* 恢复按钮的点击事件 */
}

.graph-placeholder-btn:hover {
  background-color: var(--secondary-color);
}

.node-info-panel {
  position: absolute;
  top: 20px;
  right: 20px;
  background-color: white;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 250px;
  z-index: 10;
}

.node-info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--gray-color);
}

.node-info-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--dark-color);
}

.close-info-btn {
  background: none;
  border: none;
  color: #a0aec0;
  cursor: pointer;
  font-size: 16px;
}

.node-info-content {
  font-size: 14px;
  line-height: 1.6;
}

.node-info-property {
  margin-bottom: 8px;
}

.node-info-label {
  font-weight: 500;
  color: var(--dark-color);
  margin-right: 5px;
}

.node-info-value {
  color: #4a5568;
}

.node-info-relations {
  margin-top: 15px;
}

.relation-item {
  margin-bottom: 5px;
  padding-left: 15px;
  position: relative;
}

.relation-item:before {
  content: "•";
  position: absolute;
  left: 0;
  color: var(--primary-color);
}

/* **新增：设置面板样式** */
.settings-panel {
  position: absolute;
  top: 20px;
  right: 20px;
  background-color: white;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 300px; /* 适当宽度 */
  z-index: 11; /* 确保在节点信息面板之上 */
}

.settings-header {
   display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--gray-color);
}

.settings-title {
   font-size: 16px;
  font-weight: 600;
  color: var(--dark-color);
}

.close-settings-btn {
   background: none;
  border: none;
  color: #a0aec0;
  cursor: pointer;
  font-size: 16px;
}

.settings-content {
   font-size: 14px;
   display: flex;
   flex-direction: column;
   gap: 12px;
}

.setting-item {
    display: flex;
    align-items: center;
    gap: 10px;
}

.setting-item label {
    font-weight: 500;
     color: var(--dark-color);
     min-width: 80px; /* 确保标签宽度一致 */
}

.setting-item input[type="number"] {
    width: 60px;
    padding: 4px 8px;
    border: 1px solid var(--gray-color);
    border-radius: 4px;
    font-size: 14px;
}

.setting-item input[type="range"] {
     flex: 1;
}

.setting-item span {
     min-width: 30px;
     text-align: right;
}
/* **设置面板样式结束** */

/* 移除或隐藏模拟知识图谱样式 */
.simulated-graph {
  display: none; /* 隐藏模拟图 */
}

@media (max-width: 768px) {
  .search-box {
    flex-direction: column;
  }

  .search-btn {
    padding: 12px;
  }

  .graph-tools {
    /* 在小屏幕上可能需要调整工具栏布局或隐藏部分按钮 */
    /* display: none; */
  }

  .node-info-panel, .settings-panel {
    width: calc(100% - 40px);
    right: 20px;
    left: 20px; /* 让面板居中 */
    top: auto; /* 调整位置 */
    bottom: 20px;
  }

  .graph-section {
    height: 500px;
  }
}
</style>