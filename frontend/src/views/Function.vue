<template>
  <div class="function-container">
    <!-- 移动端菜单切换按钮 -->
    <button class="mobile-menu-toggle" id="mobileMenuToggle" @click="toggleMobileMenu">
      <i class="fas fa-bars"></i>
    </button>

    <!-- 侧边栏组件 -->
    <Sidebar
        :show-user-card="showUserCard"
        :is-collapsed="isSidebarCollapsed"
        :is-mobile-open="isMobileMenuOpen"
        :active-menu="activeMenu"
        :chat-histories="chatHistories"
        :active-history-index="activeHistoryIndex"
        :user-info="userInfo"
        @toggle-sidebar="toggleSidebar"
        @set-active-menu="setActiveMenu"
        @set-active-history="setActiveHistory"
        @new-chat="newChat"
        @handle-rename="handleRename"
        @handle-delete="handleDelete"
        @toggle-user-card="toggleUserCard"
        @close-user-card="closeUserCard"
        @logout="logout"
        @go-to-profile-settings="goToProfileSettings"
    />

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 透明顶部边框 -->
      <div class="header">
        <div class="current-topic" style="font-weight: bold; color: #000;">{{ currentTopic }}</div>
        <div class="header-tools">
          <button class="create-chat-btn" @click="newChat">
            <i class="fas fa-plus"></i>
            创建新会话
          </button>
        </div>
      </div>
      <!-- 动态内容区 -->
      <div class="content-area">
        <!-- 知识图谱内容 -->
        <KnowledgeGraph
            v-if="activeMenu === 'knowledge'"
            ref="knowledgeGraph"
        />
        <!-- 习题解析内容 -->
        <ExerciseContainer
            v-if="activeMenu === 'exercise'"
            ref="exerciseContainer"
        />
        <!-- 聊天容器组件 -->
        <ChatContainer
            v-if="activeMenu === 'chat'"
            :chat-histories="chatHistories"
            :messages="messages"
            :input-message="inputMessage"
            @send-message="sendMessage"
            @upload-file="uploadFile"
            @upload-image="uploadImage"
            @upload-code="uploadCode"
            @start-recording="startRecording"
            @update-input="updateInputMessage"
            @show-error="showErrorMessage"
        />
      </div>

      <CodeQuality v-if="activeMenu === 'codeQuality'" />

      <!-- 重命名对话框 -->
      <RenameDialog
          v-if="showRenameDialog"
          :current-title="newSessionTitle"
          @confirm="confirmRename"
          @cancel="cancelRename"
          @update-title="updateSessionTitle"
      />

      <!-- 删除对话框 -->
      <DeleteDialog
          v-if="showDeleteDialog"
          @confirm="confirmDelete"
          @cancel="cancelDelete"
      />
    </div>
    <!-- 全局进度条 (固定在右侧) -->
    <div class="global-progress">
      <div class="progress-bar" :style="{height: progress + '%'}"></div>
    </div>
    <!-- 个人信息编辑模态框 -->
    <ProfileModal
        v-if="showProfileModal"
        :user-info="tempUserInfo"
        :temp-avatar-url="tempAvatarUrl"
        @close="closeProfileModal"
        @save="saveProfile"
        @trigger-avatar-upload="triggerAvatarUpload"
        @error="showErrorMessage"
    />
  </div>
</template>

<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';

// 导入组件
import Sidebar from '@/components/Sidebar.vue';
import ChatContainer from '@/components/ChatContainer.vue';
import ExerciseContainer from '@/components/ExerciseContainer.vue';
import KnowledgeGraph from '@/components/KnowledgeGraph.vue';
import RenameDialog from '@/components/Dialog/RenameDialog.vue';
import DeleteDialog from '@/components/Dialog/DeleteDialog.vue';
import ProfileModal from '@/components/Dialog/ProfileModal.vue';
import CodeQuality from '@/components/CodeQuality.vue';

export default {
  name: 'FunctionPage',
  components: {
    Sidebar,
    ChatContainer,
    ExerciseContainer,
    KnowledgeGraph,
    RenameDialog,
    DeleteDialog,
    ProfileModal,
    CodeQuality
  },
  computed: {
    activeComponent() {
      // 根据当前活动菜单返回对应组件
      const componentMap = {
        chat: 'ChatContainer',
        exercise: 'ExerciseContainer',
        // 其他菜单对应的组件...
      };
      return componentMap[this.activeMenu] || 'ChatContainer';
    },
    componentProps() {
      // 根据需要传递不同的props
      if (this.activeMenu === 'chat') {
        return {
          chatHistories: this.chatHistories,
          messages: this.messages,
          inputMessage: this.inputMessage,
          sendMessage: this.sendMessage,
          uploadFile: this.uploadFile,
          uploadImage: this.uploadImage,
          uploadCode: this.uploadCode,
          startRecording: this.startRecording,
          updateInput: this.updateInputMessage
        };
      } else if (this.activeMenu === 'exercise') {
        return {};
      }
      return {};
    }
  },
  data() {
    return {
      isSidebarCollapsed: false,
      isMobileMenuOpen: false,
      showUserCard: false,
      activeMenu: 'chat',
      activeHistoryIndex: 0,
      currentTopic: '欢迎使用SmartSE系统',
      inputMessage: '',
      isLoading: false,
      messages: [],
      chatHistories: [],
      userInfo: null,
      showRenameDialog: false,
      showDeleteDialog: false,
      currentRenameSession: null,
      currentDeleteSession: null,
      newSessionTitle: '',
      showProfileModal: false,
      isDragging: false,
      startY: 0,
      startHeight: 0,
      tempAvatarUrl: null,
      avatarFile: null,
      defaultAvatar: null,
      isUpdating: false,
      tempUserInfo: {
        nickname: '',
        bio: '',
        school: '',
        major: '',
        avatarUrl: null
      }
    };
  },
  created() {
    this.configureMarked();
  },
  beforeRouteEnter(to, from, next) {
    const userId = localStorage.getItem("userId");
    const token = localStorage.getItem("userToken");

    if (!userId || !token) {
      next('/login');
    } else {
      next(vm => {
        vm.initializeComponent();
      });
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.setupDragHandlers();
    });

  },
  updated() {
    this.$nextTick(() => {
      this.setupDragHandlers();
    });
  },
  methods: {
    configureMarked() {
      const renderer = new marked.Renderer();
      renderer.listitem = (text, task, checked) => {
        const isOrdered = this.listStack[this.listStack.length - 1] !== 'bullet';
        return `<li style="margin-left: 1.2em; text-indent: -1.2em; line-height: 1.6;">
            ${isOrdered ? '' : '• '}${text}
            </li>`;
      };
      marked.setOptions({
        gfm: true,
        breaks: true,
        tables: true, // 启用表格支持
        highlight: (code, lang) => {
          const validLang = hljs.getLanguage(lang) ? lang : 'plaintext';
          return hljs.highlight(validLang, code).value;
        }
      });
    },
    async initializeComponent() {
      try {
        await this.loadUserInfo();
        await this.loadChatHistories();

        // 检查是否有上一次的会话ID
        const lastSessionId = localStorage.getItem('lastActiveSessionId');
        if (lastSessionId) {
          // 查找会话索引
          const sessionIndex = this.chatHistories.findIndex(
              session => session.sessionId.toString() === lastSessionId
          );

          if (sessionIndex !== -1) {
            // 恢复到上一次的会话
            this.activeHistoryIndex = sessionIndex;
            this.currentTopic = this.chatHistories[sessionIndex].title;
            await this.loadSessionDetails(lastSessionId);

            // 如果需要滚动到底部
            if (localStorage.getItem('needScrollToBottom') === 'true') {
              this.$nextTick(() => {
                this.scrollToBottom();
                // 清除标记
                localStorage.removeItem('needScrollToBottom');
              });
            }
          }

          // 清除存储的会话ID
          localStorage.removeItem('lastActiveSessionId');
        }

        this.setupDragHandlers();
        this.setupClickOutsideHandlers();
        this.setupDragHandlers();
        this.setupClickOutsideHandlers();
        this.scrollToBottom();
      } catch (error) {
        console.error('初始化失败:', error);
        this.showErrorMessage('加载数据失败，请刷新页面重试');
      }
    },
    updateInputMessage(value) {
      this.inputMessage = value;
    },
    updateSessionTitle(value) {
      this.newSessionTitle = value;
    },
    async loadUserInfo() {
      try {
        const userId = localStorage.getItem("userId");
        if (!userId) {
          this.$router.push("/Login");
          return;
        }

        // 1. 尝试从API获取用户信息
        const response = await axios.get(`http://localhost:8080/api/user/profile/${userId}`, {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('userToken')}`
          }
        });

        if (response.data.code === 200) {
          const userData = response.data.data;

          // 2. 判断是否是新用户（检查关键字段是否为空）
          const isNewUser = !userData.nickname ||
              !userData.school ||
              !userData.major ||
              !userData.bio;

          // 3. 处理用户数据
          this.userInfo = {
            id: userData.id,
            userId: userData.userId,
            nickname: userData.nickname ||"",
            avatarUrl: userData.avatarUrl || this.defaultAvatar,
            bio: userData.bio ||"",
            school: userData.school || "",
            major: userData.major || "",
            createdAt: userData.createdAt,
            updatedAt: userData.updatedAt
          };

          // 4. 更新本地存储和UI
          localStorage.setItem("userInfo", JSON.stringify(this.userInfo));
          this.updateUserCard();

          // 如果是新用户，提示完善信息
          if (isNewUser) {

            //冲突解决处4
            this.userInfo = {
              nickname: userData.nickname || "新用户",
              bio: "请完善个人信息",
              school: "",
              major: "",
              avatarUrl: userData.avatarUrl || null
            };
            // this.showWarningMessage("请完善您的个人信息");
          } else {
            this.userInfo = userData;


          }
        }
      } catch (error) {
        console.error("获取用户信息出错:", error);
        this.setDefaultUserInfo();
      }
    },
    setDefaultUserInfo() {
      // 从localStorage获取基本信息
      const userInfoStr = localStorage.getItem("userInfo");
      const cached = userInfoStr ? JSON.parse(userInfoStr) : {};

      this.userInfo = {
        nickname: cached.username || "新用户", // 使用用户名作为默认昵称
        bio: "",
        school: "",
        major: "",
        avatarUrl: null,
        ...cached // 保留已存在的属性
      };

      this.updateUserCard();
    },
    updateUserCard() {
      if (!this.userInfo) return;

      // 使用requestAnimationFrame确保DOM更新时机
      requestAnimationFrame(() => {
        // 头像更新（改用更可靠的属性设置方式）
        const avatarElements = document.querySelectorAll('[class*="avatar"]');
        avatarElements.forEach(el => {
          if (this.userInfo.avatarUrl) {
            //   // 完全替换img的src属性（如果使用img标签）
            //   if (el.tagName === 'IMG') {
            //     el.src = `${this.userInfo.avatarUrl.split('?')[0]}?t=${Date.now()}`;
            //   }
            // 处理背景图方式
            // else {
            el.style.backgroundImage = `url('${this.userInfo.avatarUrl}')`;
            el.style.backgroundSize = 'cover';
            el.style.backgroundPosition = 'center';
            el.innerHTML = ''; // 清空可能存在的文字
            // }
          } else {
            // 处理默认头像逻辑
            el.style.backgroundImage = 'none';
            const initial = this.userInfo.nickname?.charAt(0) ||
                this.userInfo.username?.charAt(0) || 'U';
            el.textContent = initial.toUpperCase();
          }
        });

        // 用户名更新（更健壮的选择器）
        const nameSelectors = [
          '.user-name',
          '.user-card-info h3',
          '[data-user-name]'
        ].join(',');

        document.querySelectorAll(nameSelectors).forEach(el => {
          el.textContent = this.userInfo.nickname || this.userInfo.username || '用户';
        });

        // 其他信息更新
        const bioSelectors = [
          '.user-bio',
          '.user-card-info p',
          '[data-user-bio]'
        ].join(',');

        document.querySelectorAll(bioSelectors).forEach(el => {
          el.textContent = [
            this.userInfo.bio || '',
            this.userInfo.school || '',
            this.userInfo.major || ''
          ].filter(Boolean).join(' · ');
        });
      });
      this.$nextTick(() => {
        const avatars = document.querySelectorAll('.user-avatar, .user-card-avatar');
        avatars.forEach(avatar => {
          if (this.userInfo.avatarUrl) {
            avatar.style.backgroundImage = `url('${this.userInfo.avatarUrl}')`;
            avatar.innerHTML = '';
          } else {
            avatar.style.backgroundImage = 'none';
            avatar.textContent = this.userInfo.nickname?.charAt(0) || 'U';
          }
        });
      });
    },
    async loadChatHistories() {
      try {
        const userId = localStorage.getItem("userId");
        const token = localStorage.getItem("userToken");

        //冲突解决处5
        if (!userId || !token) {
          this.showWarningMessage("请先登录");
          this.$router.push('/login');
          return;
        }


        const response = await axios.post('http://localhost:8080/api/chat/sessions', {
          userId: userId,
          page: 1,
          size: 10
        });

        if (response.data.code === 200) {
          console.log('Raw chat histories:', response.data.data);

          // 确保返回的数据是数组
          const sessions = Array.isArray(response.data.data) ? response.data.data : [];

          this.chatHistories = sessions.map(session => ({
            sessionId: session.sessionId || session.id,
            title: session.title || session.sessionTitle || '新会话',
            time: new Date(session.createdAt || session.createTime).toLocaleString(),
            senderType: session.senderType || 'user'
          }));

          console.log('Mapped chat histories:', this.chatHistories);

          // 如果有历史记录，加载第一个会话
          if (this.chatHistories.length > 0) {
            this.activeHistoryIndex = 0;
            this.currentTopic = this.chatHistories[0].title || '新会话';
            await this.loadSessionDetails(this.chatHistories[0].sessionId);
          }
        }
      } catch (error) {
        console.error('加载会话历史失败:', error);
        this.showErrorMessage("加载会话历史失败: " + (error.response?.data?.message || error.message));
      }
    },
    async loadSessionDetails(sessionId) {
      try {
        const response = await axios.post('http://localhost:8080/api/chat/session/load', {
          sessionId: sessionId.toString()
        });

        if (response.data.code === 200) {
          const sessionData = response.data.data;
          this.currentTopic = sessionData.sessionTitle || sessionData.title || '新会话';

          const rawMessages = sessionData.chatMessages || [];
          this.messages = []; // 清空现有消息

          let i = 0;
          while (i < rawMessages.length) {
            const currentMsg = rawMessages[i];

            // 处理用户消息
            if (currentMsg.senderType.toLowerCase() === 'user') {
              this.messages.push({
                type: 'user',
                content: currentMsg.content || '',
                renderedContent: DOMPurify.sanitize(marked.parse(currentMsg.content || '')), // 预渲染用户消息
                time: new Date(currentMsg.createdAt).toLocaleTimeString(),
                rawData: currentMsg // 保留原始数据
              });

              // 查找紧随其后的助手消息
              const nextMsg = rawMessages[i + 1];
              if (nextMsg && nextMsg.senderType.toLowerCase() === 'assistant') {
                // 组合思考过程和最终回答
                let botContent = nextMsg.content || '';
                const reasoningProcess = currentMsg.reasoningProcess || '';

                if (reasoningProcess) {
                  // 使用特殊字符来标记思考过程，这些字符在显示时会被隐藏
                  botContent = `[REASONING_START]${reasoningProcess}[REASONING_END]${botContent}`;
                }
                this.messages.push({
                  type: 'bot',
                  content: botContent,
                  renderedContent: null, // 让ChatContainer组件处理包含标记的内容
                  time: new Date(nextMsg.createdAt).toLocaleTimeString(),
                  agent: '智能助手',
                  rawData: nextMsg, // 保留原始数据
                  hideReasoningTags: true // 添加标记，表示需要隐藏标签
                });
                i += 2; // 跳过用户消息和其后的助手消息
              } else {
                // 如果用户消息后没有紧随的助手消息，只处理用户消息
                i += 1;
              }
            } else if (currentMsg.senderType.toLowerCase() === 'assistant') {
              // 处理没有对应用户消息的独立助手消息（理论上不应该出现，但作为容错）
              this.messages.push({
                type: 'bot',
                content: currentMsg.content || '',
                renderedContent: DOMPurify.sanitize(marked.parse(currentMsg.content || '')),
                time: new Date(currentMsg.createdAt).toLocaleTimeString(),
                agent: '智能助手',
                rawData: currentMsg
              });
              i += 1;
            } else {
              // 跳过其他类型的消息（如 system）
              i += 1;
            }
          }
          this.scrollToBottom();
        }
      } catch (error) {
        console.error("加载会话详情失败:", error);
        this.showErrorMessage("加载失败: " + (error.response?.data?.message || error.message));

        this.messages = [{
          type: 'system',
          content: '加载会话失败，请重试或创建新会话',
          renderedContent: '加载会话失败，请重试或创建新会话',
          time: this.getCurrentTime()
        }];
      }
    },
    goToProfileSettings() {
      console.log('goToProfileSettings called'); // 调试信息
      this.showProfileModal = true;
      this.showUserCard = false; // 关闭用户卡片
      // 复制当前用户信息到临时对象
      this.tempUserInfo = {
        nickname: this.userInfo?.nickname || '',
        bio: this.userInfo?.bio || '',
        school: this.userInfo?.school || '',
        major: this.userInfo?.major || '',
        avatarUrl: this.userInfo?.avatarUrl || null
      };
      this.tempAvatarUrl = this.userInfo?.avatarUrl || null;
      this.avatarFile = null;
    },
    closeProfileModal() {
      this.showProfileModal = false;
      // 确保视图更新
      this.updateUserCard();
      this.$nextTick(() => {
        this.$forceUpdate();
      });
    },

    triggerAvatarUpload() {
      this.$refs.avatarInput.click();
    },

// 修改handleAvatarUpload方法，添加压缩逻辑
    async handleAvatarUpload(event) {
      const file = event.target.files[0];
      if (!file) return;

      // 验证文件类型和大小
      if (!file.type.match('image.*')) {
        this.$message.error('请选择有效的图片文件');
        return;
      }
      if (file.size > 2 * 1024 * 1024) {
        this.$message.error('图片大小不能超过2MB');
        return;
      }

      // 压缩图片
      try {
        const compressedFile = await this.compressImage(file);
        this.avatarFile = compressedFile;

        // 创建预览
        const reader = new FileReader();
        reader.onload = (e) => {
          this.tempAvatarUrl = e.target.result;
        };
        reader.readAsDataURL(compressedFile);
      } catch (error) {
        console.error('图片压缩失败:', error);
        this.$message.error('图片处理失败');
      }
    },

// 图片压缩方法
    compressImage(file, maxWidth = 800, quality = 0.7) {
      return new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = (event) => {
          const img = new Image();
          img.src = event.target.result;
          img.onload = () => {
            const canvas = document.createElement('canvas');
            const scale = Math.min(maxWidth / img.width, 1);
            canvas.width = img.width * scale;
            canvas.height = img.height * scale;

            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

            canvas.toBlob(
                (blob) => resolve(blob),
                file.type,
                quality
            );
          };
        };
        reader.readAsDataURL(file);
      });
    },

    async saveProfile({ userInfo, avatarFile }) {
      if (this.isUpdating) return;
      this.isUpdating = true;

      try {
        const userId = localStorage.getItem("userId");
        if (!userId) {
          this.$router.push("/Login");
          return;
        }

        const updateData = {
          userId: userId,
          nickname: userInfo.nickname,
          bio: userInfo.bio,
          school: userInfo.school,
          major: userInfo.major
        };

        // 如果有新头像，转换为Base64
        if (avatarFile) {
          this.tempAvatarUrl = await this.readFileAsBase64(avatarFile);
          this.userInfo.avatarUrl = this.tempAvatarUrl;

          // 然后再发送到后端
          updateData.avatarBase64 = this.tempAvatarUrl;
        }
        const response = await axios.put(
            'http://localhost:8080/api/user/profile',
            updateData,
            {
              headers: {
                'Authorization': `Bearer ${localStorage.getItem('userToken')}`,
                'Content-Type': 'application/json'
              }
            }
        );

        if (response.data.code === 200) {
          this.userInfo = {
            ...this.userInfo,
            ...userInfo,
            avatarUrl: response.data.data.avatarUrl || this.userInfo.avatarUrl
          };
          // 同步更新 tempUserInfo（用于编辑）
          this.tempUserInfo = { ...this.userInfo };
          localStorage.setItem("userInfo", JSON.stringify(this.userInfo));
          this.$message.success('个人信息更新成功');
          this.closeProfileModal();
          // 强制更新视图
          this.$forceUpdate();
        }
      } catch (error) {
        console.error('保存个人信息出错:', error);
        this.$message.error('保存失败: ' + (error.response?.data?.message || error.message));
      } finally {
        this.isUpdating = false;
      }
    },
    // 新增方法：将文件读取为base64
    readFileAsBase64(file) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => resolve(e.target.result);
        reader.onerror = (error) => reject(error);
        reader.readAsDataURL(file);
      });
    },
    // 修改退出登录功能
    logout() {
      localStorage.removeItem('userToken')
      localStorage.removeItem('userId');
      localStorage.removeItem('userInfo')
      this.userInfo = null;
      this.$router.push('/')
    },
    toggleSidebar() {
      this.isSidebarCollapsed = !this.isSidebarCollapsed;
    },
    toggleMobileMenu() {
      this.isMobileMenuOpen = !this.isMobileMenuOpen;
    },
    toggleUserCard(event) {
      console.log('toggleUserCard called'); // 调试信息
      event = event || {};
      if (event.stopPropagation) {
        event.stopPropagation();
      }
      this.showUserCard = !this.showUserCard;
      console.log('showUserCard:', this.showUserCard); // 查看状态变化
    },
    closeUserCard(event) {
      event = event || {};
      // 检查stopPropagation是否存在
      if (event.stopPropagation) {
        event.stopPropagation();
      }
      this.showUserCard = false;
    },
    setActiveMenu(menu) {
      this.activeMenu = menu;
      this.currentTopic = this.getMenuTitle(menu);
      if (window.innerWidth <= 768) {
        this.isMobileMenuOpen = false;
      }
    },
    getMenuTitle(menu) {
      const titles = {
        chat: '智能对话',
        knowledge: '知识图谱',
        exercise: '习题解析',
      };
      return titles[menu] || '欢迎使用SmartSE系统';
    },
//冲突解决处6
    async setActiveHistory(index) {
      try {
        if (index < 0 || index >= this.chatHistories.length) {
          console.warn('Invalid history index:', index);
          return;
        }
        // 如果点击的是当前活动的会话，不需要重新加载
        if (index === this.activeHistoryIndex) {
          console.log('Already on this session, skipping reload');
          return;
        }

        const selectedHistory = this.chatHistories[index];
        if (!selectedHistory || !selectedHistory.sessionId) {
          console.warn('Invalid history data at index:', index);
          return;
        }

        console.log('Switching to session:', selectedHistory);

        this.activeHistoryIndex = index;
        this.currentTopic = selectedHistory.title || '新会话';
        // 无论当前在哪个界面，都切换到聊天界面
        this.activeMenu = 'chat';

        // 显示加载中的提示
        this.messages = [{
          type: 'system',
          content: '正在加载会话历史...',
          time: this.getCurrentTime()
        }];

        await this.loadSessionDetails(selectedHistory.sessionId);

        if (window.innerWidth <= 768) {
          this.isMobileMenuOpen = false;
        }
      } catch (error) {
        console.error('切换会话失败:', error);
        this.showErrorMessage('切换会话失败: ' + error.message);
      }
    },
    async newChat() {
      try {
        const userId = localStorage.getItem("userId");
        if (!userId) {
          this.showWarningMessage("请先登录");
          return;
        }

        this.messages = [{
          type: 'system',
          content: '正在创建新会话...',
          time: this.getCurrentTime()
        }];

//冲突解决处7
        const response = await axios.post('http://localhost:8080/api/chat/session/create', {
          userId: userId,
          sessionTitle: `新会话 ${this.getCurrentTime()}`
        });

        if (response.data.code === 200) {
          const sessionId = response.data.data.sessionId;
          localStorage.setItem('currentSessionId', sessionId);
          console.log('Created new session with ID:', sessionId);

          const newSession = {
            sessionId: sessionId,
            title: response.data.data.sessionTitle || `新会话 ${this.getCurrentTime()}`,
            time: this.getCurrentTime(),
            senderType: 'system'
          };

          this.chatHistories.unshift(newSession);
          this.activeHistoryIndex = 0;
          this.currentTopic = newSession.title;

          this.messages = [{
            type: 'bot',
            content: '欢迎使用SmartSE软件工程课程智能助手！这是一个全新的会话。请问您需要了解什么内容？',
            time: this.getCurrentTime(),
            agent: '通用助手',
            isNew: true
          }];

          this.scrollToBottom();
          return true; // 返回成功标志
        } else {
          throw new Error(response.data.message || '创建会话失败');
        }
      } catch (error) {
        console.error("创建新会话失败:", error);
        this.showErrorMessage("创建会话失败: " + (error.response?.data?.message || error.message));
        return false; // 返回失败标志
      }
    },
    sendMessage: async function (formData) {
      try {
        // 获取消息内容和文件
        const message = formData.get('message') || '';
        const files = [];

        // 检查是否有文件
        for (const [key, value] of formData.entries()) {
          if (value instanceof File) {
            console.log(`找到文件: ${key}, 文件名: ${value.name}, 类型: ${value.type}, 大小: ${value.size}字节`);
            files.push(value);
          }
        }

        const hasFiles = files.length > 0;

        // 验证是否有消息内容或文件
        if (!message.trim() && !hasFiles) {
          console.log('没有消息或文件，不发送请求');
          return;
        }

        // 获取当前会话ID
        let currentSessionId = null;
        if (this.activeHistoryIndex >= 0 && this.chatHistories[this.activeHistoryIndex]) {
          currentSessionId = this.chatHistories[this.activeHistoryIndex].sessionId;
        }

        // 添加用户消息到界面
        this.messages.push({
          type: 'user',
          content: message.trim() || (hasFiles ? '发送了文件' : ''),
          time: this.getCurrentTime(),
          files: hasFiles ? files.map(file => ({
            name: file.name,
            size: this.formatFileSize(file.size),
            type: file.type,
            url: URL.createObjectURL(file)
          })) : []
        });

        // 添加临时机器人消息
        const botMessage = {
          type: 'bot',
          content: '',
          time: this.getCurrentTime(),
          agent: '智能助手',
          isStreaming: true
        };
        this.messages.push(botMessage);

        this.inputMessage = ''; // 清空输入框
        this.scrollToBottom();

        // 准备发送到后端的数据
        const apiFormData = new FormData();
        apiFormData.append('message', message);
        apiFormData.append('userId', localStorage.getItem("userId"));
        apiFormData.append('sessionId', currentSessionId);

        // 添加文件 - 检查类型并正确命名参数
        if (hasFiles) {
          for (const file of files) {
            if (file.type.startsWith('image/')) {
              console.log(`添加图片到请求: ${file.name}`);
              apiFormData.append('image', file);
            } else {
              console.log(`添加文件到请求: ${file.name}`);
              apiFormData.append('file', file);
            }
          }
        }

        // 打印发送的表单数据内容（调试用）
        console.log('准备发送请求，表单数据包含:');
        for (const [key, value] of apiFormData.entries()) {
          if (value instanceof File) {
            console.log(`- ${key}: 文件(${value.name}, ${value.type}, ${value.size}字节)`);
          } else {
            console.log(`- ${key}: ${value}`);
          }
        }

        this.isLoading = true;

        // 使用fetch发送请求
        const response = await fetch('http://localhost:8080/api/chat/stream_chat', {
          method: 'POST',
          body: apiFormData,
        });

        if (!response.ok) {
          throw new Error(`HTTP错误! 状态码: ${response.status}`);
        }

        // 在Function.vue中的sendMessage方法中修改流式处理部分
        const reader = response.body.getReader();
        const decoder = new TextDecoder();

// 流式处理响应
        // 修改 Function.vue 中的 sendMessage 方法中的流式处理部分
        // 流式处理响应
        let buffer = '';  // Store the ongoing data
        let plantUmlBuffer = '';  // Store PlantUML-specific data (separate from general content)

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          const text = decoder.decode(value, { stream: true });
          buffer += text;  // Append new data to the buffer

          // Split the buffer into events using '\n\n' as the delimiter
          const events = buffer.split('\n\n');
          buffer = events.pop() || '';  // Keep the last incomplete event in the buffer

          for (const eventText of events.filter(line => line.trim())) {
            if (!eventText.startsWith('data:')) continue;

            // Extract all 'data:' lines
            let messageContent = eventText.split('\n')
                .filter(line => line.startsWith('data:'))
                .map(line => line.substring(5).trim())
                .join('\n');

            if (messageContent === "[DONE]") {
              this.isLoading = false;
              const lastBotMessage = this.messages[this.messages.length - 1];
              if (lastBotMessage.type === 'bot') {
                lastBotMessage.isStreaming = false;
              }
              this.$forceUpdate();
              return;
            }

            // Handle PlantUML content
            if (messageContent.includes('@startuml') || plantUmlBuffer) {
              // Append to the PlantUML buffer until we reach the full PlantUML block
              plantUmlBuffer += messageContent;

              // Check if we have a complete PlantUML block
              if (plantUmlBuffer.includes('@enduml')) {
                // Once we have the full PlantUML code, we can process it
                const lastBotMessage = this.messages[this.messages.length - 1];
                if (lastBotMessage.type === 'bot') {
                  lastBotMessage.content += plantUmlBuffer;

                  // Handle Markdown rendering for other message content
                  if (this.$refs.chatContainer) {
                    const chatContainer = this.$refs.chatContainer;

                    if (typeof chatContainer.renderMarkdown === 'function') {
                      lastBotMessage.renderedContent = chatContainer.renderMarkdown(lastBotMessage.content);
                    } else if (typeof chatContainer.renderMarkdownBlocks === 'function') {
                      lastBotMessage.renderedContent = chatContainer.renderMarkdownBlocks(lastBotMessage.content);
                    } else {
                      lastBotMessage.renderedContent = DOMPurify.sanitize(marked.parse(lastBotMessage.content));
                    }
                  } else {
                    lastBotMessage.renderedContent = DOMPurify.sanitize(marked.parse(lastBotMessage.content));
                  }
                }

                // Reset PlantUML buffer for the next block
                plantUmlBuffer = '';
                this.$forceUpdate();
              }
            } else {
              // Non-PlantUML data, append it to the last bot message content
              const lastBotMessage = this.messages[this.messages.length - 1];
              if (lastBotMessage.type === 'bot') {
                lastBotMessage.content += messageContent;

                if (this.$refs.chatContainer) {
                  const chatContainer = this.$refs.chatContainer;

                  if (typeof chatContainer.renderMarkdown === 'function') {
                    lastBotMessage.renderedContent = chatContainer.renderMarkdown(lastBotMessage.content);
                  } else if (typeof chatContainer.renderMarkdownBlocks === 'function') {
                    lastBotMessage.renderedContent = chatContainer.renderMarkdownBlocks(lastBotMessage.content);
                  } else {
                    lastBotMessage.renderedContent = DOMPurify.sanitize(marked.parse(lastBotMessage.content));
                  }
                } else {
                  lastBotMessage.renderedContent = DOMPurify.sanitize(marked.parse(lastBotMessage.content));
                }
                this.$forceUpdate();
              }
            }
          }
        }
      } catch (error) {
        console.error("发送消息失败:", error);
        this.showErrorMessage("发送失败: " + (error.response?.data || error.message));
        // 移除临时的"正在输入"消息
        this.messages = this.messages.filter(msg => !msg.isStreamming);
      } finally {
        this.isLoading = false;
        this.scrollToBottom();
      }
    },

    formatFileSize(bytes) {
      if (bytes === 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },
    getFilesFromFormData(formData) {
      const files = [];
      for (const [key, value] of formData.entries()) {
        if (key.startsWith('file') && value instanceof File) {
          files.push(value);
        }
      }
      return files;
    },
    getCurrentTime() {
      const now = new Date();
      return `${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}`;
    },
    uploadFile() {
      // 文件上传逻辑
      console.log('上传文件');
    },
    uploadImage() {
      // 图片上传逻辑
      console.log('上传图片');
    },
    uploadCode() {
      // 代码上传逻辑
      console.log('上传代码');
    },
    startRecording() {
      // 录音逻辑
      console.log('开始录音');
    },
    setupDragHandlers() {
      this.$nextTick(() => {
        const inputContainer = this.$refs.inputContainer;
        const chatContainer = this.$refs.chatContainer;

        if (!inputContainer) return;

        // 移除已存在的拖动条（避免重复添加）
        const existingHandle = inputContainer.querySelector('.drag-handle');
        if (existingHandle) {
          existingHandle.remove();
        }

        // 创建拖动条元素
        const dragHandle = document.createElement('div');
        dragHandle.className = 'drag-handle';
        dragHandle.style.height = '10px';
        dragHandle.style.width = '100%';
        dragHandle.style.position = 'absolute';
        dragHandle.style.top = '0';
        dragHandle.style.cursor = 'ns-resize';
        dragHandle.style.zIndex = '10';
        inputContainer.prepend(dragHandle);
        console.log("Drag handle created:", dragHandle);
        // 拖动开始
        dragHandle.addEventListener('mousedown', (e) => {
          console.log("Drag started!"); // 检查是否触发
          this.isDragging = true;
          this.startY = e.clientY;
          this.startHeight = parseInt(window.getComputedStyle(inputContainer).height, 10);
          e.preventDefault();
        });

        // 拖动过程
        document.addEventListener('mousemove', (e) => {
          if (!this.isDragging) return;

          const deltaY = this.startY - e.clientY;
          const newHeight = this.startHeight + deltaY;

          // 限制最小和最大高度
          if (newHeight > 80 && newHeight < 300) {
            inputContainer.style.height = newHeight + 'px';
            if (chatContainer) {
              chatContainer.style.marginBottom = (newHeight + 20) + 'px';
            }
          }
        });

        // 拖动结束
        document.addEventListener('mouseup', () => {
          this.isDragging = false;
        });

        // 触摸设备支持
        dragHandle.addEventListener('touchstart', (e) => {
          this.isDragging = true;
          this.startY = e.touches[0].clientY;
          this.startHeight = parseInt(window.getComputedStyle(inputContainer).height, 10);
          e.preventDefault();
        });

        document.addEventListener('touchmove', (e) => {
          if (!this.isDragging) return;

          const deltaY = this.startY - e.touches[0].clientY;
          const newHeight = this.startHeight + deltaY;

          if (newHeight > 80 && newHeight < 300) {
            inputContainer.style.height = newHeight + 'px';
            if (chatContainer) {
              chatContainer.style.marginBottom = (newHeight + 20) + 'px';
            }
          }
        });

        document.addEventListener('touchend', () => {
          this.isDragging = false;
        });
      });
    },
    setupClickOutsideHandlers() {
      // 点击用户卡片外部关闭卡片
      document.addEventListener('click', (e) => {
        const userCard = document.getElementById('userCard');
        const userProfileButton = document.getElementById('userProfileButton');

        if (this.showUserCard &&
            userCard &&
            !userCard.contains(e.target) &&
            userProfileButton &&
            !userProfileButton.contains(e.target)) {
          this.showUserCard = false;
        }

        // 移动端点击侧边栏外部关闭菜单
        if (window.innerWidth <= 768 &&
            this.isMobileMenuOpen &&
            document.getElementById('sidebar') &&
            !document.getElementById('sidebar').contains(e.target) &&
            e.target !== document.getElementById('mobileMenuToggle')) {
          this.isMobileMenuOpen = false;
        }
      });
    },
    scrollToBottom() {
      const chatContainer = this.$refs.chatContainer;
      if (chatContainer) {
        chatContainer.scrollTop = chatContainer.scrollHeight;
      }
    },
    async renameSession(sessionId, newTitle) {
      try {
        const response = await axios.post('http://localhost:8080/api/chat/session/rename', {
          sessionId: sessionId.toString(), // 确保是字符串
          sessionTitle: newTitle
        });

        if (response.data.code === 200) {
          const sessionIndex = this.chatHistories.findIndex(s => s.sessionId === sessionId);
          if (sessionIndex !== -1) {
            this.chatHistories[sessionIndex].title = newTitle;
            if (this.activeHistoryIndex === sessionIndex) {
              this.currentTopic = newTitle;
            }
          }
          this.showSuccessMessage('重命名成功');
        } else {
          throw new Error(response.data.message || '重命名失败');
        }
      } catch (error) {
        console.error("重命名会话失败:", error);
        this.showErrorMessage("重命名失败: " + (error.response?.data?.message || error.message));
      }
    },
    async deleteSession(sessionId) {
      try {
        const response = await axios.post('http://localhost:8080/api/chat/session/delete', {
          sessionId: sessionId.toString() // 确保是字符串
        });

        if (response.data.code === 200) {
          const sessionIndex = this.chatHistories.findIndex(s => s.sessionId === sessionId);
          if (sessionIndex !== -1) {
            this.chatHistories.splice(sessionIndex, 1);

            if (this.activeHistoryIndex === sessionIndex) {
              if (this.chatHistories.length > 0) {
                this.setActiveHistory(0);
              } else {
                this.messages = [];
                this.currentTopic = '新会话';
                this.activeHistoryIndex = -1;
              }
            } else if (this.activeHistoryIndex > sessionIndex) {
              this.activeHistoryIndex--;
            }
          }
          this.showSuccessMessage('删除成功');
          return true;
        } else {
          throw new Error(response.data.message || '删除失败');
        }
      } catch (error) {
        console.error("删除会话失败:", error);
        this.showErrorMessage("删除失败: " + (error.response?.data?.message || error.message));
        return false;
      }
    },
    handleRename(history) {
      this.currentRenameSession = history;
      this.newSessionTitle = history.title;
      this.showRenameDialog = true;
      // 在下一个 tick 后聚焦输入框
      this.$nextTick(() => {
        const input = this.$refs.sessionTitleInput;
        if (input) {
          input.focus();
          input.select(); // 全选文本内容
        }
      });
    },
    async confirmRename() {
      if (!this.newSessionTitle.trim()) {
        this.showErrorMessage('会话名称不能为空');
        return;
      }

      try {
        await this.renameSession(this.currentRenameSession.sessionId, this.newSessionTitle);
        this.showRenameDialog = false;
        this.currentRenameSession = null;
        this.newSessionTitle = '';
      } catch (error) {
        console.error('重命名失败:', error);
      }
    },
    cancelRename() {
      this.showRenameDialog = false;
      this.currentRenameSession = null;
      this.newSessionTitle = '';
    },
    async handleDelete(history) {
      this.currentDeleteSession = history;
      this.showDeleteDialog = true;
    },
    async confirmDelete() {
      try {
        if (this.currentDeleteSession) {
          const success = await this.deleteSession(this.currentDeleteSession.sessionId);
          if (success) {
            this.showDeleteDialog = false;
            this.currentDeleteSession = null;
          }
        }
      } catch (error) {
        console.error('删除失败:', error);
      }
    },
    cancelDelete() {
      this.showDeleteDialog = false;
      this.currentDeleteSession = null;
    },
    showSuccessMessage(message) {
      this.$message.success(message); // 使用 Element Plus 的 $message
    },

    showErrorMessage(message) {
      this.$message.error(message);
    },

    showWarningMessage(message) {
      this.$message.warning(message);
    }
  }
};
</script>

<style scoped>
/* 只保留最外层的容器样式和移动端切换按钮样式 */
.function-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
  position: relative;
}

.mobile-menu-toggle {
  display: none;
  position: fixed;
  top: 15px;
  left: 15px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  font-size: 18px;
  z-index: 101;
  cursor: pointer;
}

@media (max-width: 768px) {
  .mobile-menu-toggle {
    display: block;
  }
}

@keyframes slide-up {
  0% { transform: translateY(20px); opacity: 0; }
  100% { transform: translateY(0); opacity: 1; }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .user-card {
    position: fixed;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    width: 90%;
    max-width: 320px;
  }
  .rename-dialog {
    width: 90%;
    margin: 20px;
  }

  .dialog-header {
    padding: 15px;
  }
  .dialog-header h3 {
    margin: 0;
    font-size: 18px;
    color: var(--dark-color);
    font-weight: 600;
  }

  .dialog-footer {
    padding: 15px;
  }
}
.create-chat-btn {
  background-color: var(--primary-color);
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s;
}
.create-chat-btn:hover {
  background-color: var(--secondary-color);
  transform: translateY(-1px);
}

.create-chat-btn i {
  font-size: 12px;
}
/* 主内容区样式 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-left: 280px;
  transition: margin-left 0.3s;
  position: relative;
}

.sidebar.collapsed ~ .main-content {
  margin-left: 70px;
}

.sidebar.collapsed ~ .main-content .header {
  left: 70px; /* 侧边栏折叠时的位置 */
}

.main-content {
  margin-left: 70px;
  padding-top: 60px;
}
/* 透明顶部边框 */
.header {
  padding: 15px 20px;
  background-color: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(5px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;/* 提高z-index确保在最上层 */
  position:fixed;
  top: 0;
  left: 280px; /* 与侧边栏宽度一致 */
  right: 0;
  transition: left 0.3s ease; /* 添加过渡效果 */
  text-align: center;
}
.current-topic {
  display: inline-block; /* 使标题可以居中 */
  margin: auto; /* 水平居中 */
}
.header-tools {
  position: absolute;
  right: 20px; /* 距离右侧20px */
  top: 50%; /* 垂直居中 */
  transform: translateY(-50%); /* 精确垂直居中 */
  display: flex;
  gap: 10px;
}
@media (max-width: 768px) {
  .current-topic {
    max-width: 50%; /* 移动端缩小标题最大宽度 */
    left: calc(50% + 35px); /* 考虑侧边栏折叠状态 */
  }

  .sidebar.collapsed ~ .main-content .current-topic {
    left: calc(50% + 10px); /* 侧边栏折叠时的调整 */
  }
  .sidebar.collapsed ~ .main-content {
    margin-left: 0;
  }
}
.drag-handle {
  background-color: rgba(0, 0, 0, 0.1) !important;
  height: 10px !important;
  width: 100% !important;
  position: absolute !important;
  top: 0 !important;
  cursor: ns-resize !important;
  z-index: 10 !important;
}

.drag-handle:hover {
  background-color: rgba(0, 0, 0, 0.2);
}
/*习题解析部分样式*/
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-left: 280px;
  transition: margin-left 0.3s ease;
}

.sidebar.collapsed ~ .main-content {
  margin-left: 70px;
}
</style>