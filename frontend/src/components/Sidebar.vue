<template>
  <div class="sidebar" :class="{ 'collapsed': isCollapsed, 'active': isMobileOpen }" id="sidebar">
    <div class="logo">
      <h1>SmartSE</h1>
      <span>软件工程课程智能助手</span>
      <button class="toggle-sidebar" id="toggleSidebar" @click="$emit('toggle-sidebar')">
        <i class="fas" :class="isCollapsed ? 'fa-chevron-right' : 'fa-chevron-left'"></i>
      </button>
    </div>

    <div class="menu">
      <div class="menu-section">
        <h3>导航</h3>
        <div
            class="menu-item"
            :class="{ 'active': activeMenu === 'chat' }"
            data-menu="chat"
            @click="$emit('set-active-menu', 'chat')"
        >
          <i class="fas fa-comments"></i>
          <span style="font-family: 'KaiTi', '楷体', 'STKaiti', serif; font-size: 18px; font-weight: bold; color: #000;">智能对话</span>
        </div>
        <div
            class="menu-item"
            :class="{ 'active': activeMenu === 'knowledge' }"
            data-menu="knowledge"
            @click="$emit('set-active-menu', 'knowledge')"
        >
          <i class="fas fa-project-diagram"></i>
          <span style="font-family: 'KaiTi', '楷体', 'STKaiti', serif; font-size: 18px; font-weight: bold; color: #000;">知识图谱</span>
        </div>
        <div
            class="menu-item"
            :class="{ 'active': activeMenu === 'codeQuality' }"
            data-menu="codeQuality"
            @click="$emit('set-active-menu', 'codeQuality')"
        >
          <i class="fas fa-code"></i>
          <span style="font-family: 'KaiTi', '楷体', 'STKaiti', serif; font-size: 18px; font-weight: bold; color: #000;">代码评估</span>
        </div>

<!--        <div-->
<!--            class="menu-item"-->
<!--            :class="{ 'active': activeMenu === 'exercise' }"-->
<!--            data-menu="exercise"-->
<!--            @click="$emit('set-active-menu', 'exercise')"-->
<!--        >-->
<!--          <i class="fas fa-tasks"></i>-->
<!--          <span style="font-family: 'KaiTi', '楷体', 'STKaiti', serif; font-size: 18px; font-weight: bold; color: #000;">习题推荐</span>-->
<!--        </div>-->
        <!--<div
            class="menu-item"
            :class="{ 'active': activeMenu === 'resource' }"
            data-menu="resource"
            @click="$emit('set-active-menu', 'resource')"
        >
          <i class="fas fa-book"></i>
          <span>课程资源</span>
        </div>-->
      </div>

      <div class="menu-section">
        <h3>对话历史</h3>
        <div class="chat-history-container">
          <div class="chat-history">
            <div
                v-for="(history, index) in chatHistories"
                :key="history.sessionId"
                class="chat-history-item"
                :class="{ 'active': activeHistoryIndex === index }"
            >
              <div class="history-content" @click="$emit('set-active-menu', 'chat');$emit('set-active-history', index)">
                <div class="history-title">{{ history.title }}</div>
                <div class="history-time">{{ history.time }}</div>
              </div>
              <div class="history-actions">
                <button class="action-btn" @click.stop="$emit('handle-rename', history)" title="重命名">
                  <i class="fas fa-edit"></i>
                </button>
                <button class="action-btn" @click.stop="$emit('handle-delete', history)" title="删除">
                  <i class="fas fa-trash"></i>
                </button>
              </div>
            </div>
            <div v-if="chatHistories.length === 0" class="no-history">
              <i class="fas fa-comments"></i>
              <p>暂无对话历史</p>
              <button class="create-chat-btn" @click="$emit('new-chat')" v-if="chatHistories.length === 0">
                <i class="fas fa-plus"></i>
                创建新会话
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="user-profile" @click="handleToggleUserCard">
      <div class="user-avatar">
        <span>{{ userInitial }}</span>
      </div>
      <div class="user-info">
        <div class="user-name">{{ userInfo?.nickname || '用户' }}</div>
        <div class="user-status">在线</div>
      </div>
    </div>

    <!-- 用户信息卡片 -->
    <div class="user-card-container">
    <div class="user-card" id="userCard" v-show="showUserCard">
      <div class="user-card-header">
        <div class="user-card-avatar">
          <span>{{ userInitial }}</span>
        </div>
        <div class="user-card-info">
          <h3>{{ userInfo?.nickname || '用户' }}</h3>
          <p v-if="userInfo">{{ [userInfo.bio, userInfo.school, userInfo.major].filter(Boolean).join(' · ') }}</p>
        </div>
        <button class="user-card-close" id="closeUserCard" @click="$emit('close-user-card')">
          <i class="fas fa-times"></i>
        </button>
      </div>
      <div class="user-card-content">
        <div class="user-stats">
          <div class="stat-item">
            <div class="stat-value">168</div>
            <div class="stat-label">对话次数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">32</div>
            <div class="stat-label">学习天数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">60</div>
            <div class="stat-label">已完成习题</div>
          </div>
        </div>
        <div class="user-menu">
          <a href="#" class="user-menu-item" @click.prevent="$emit('go-to-profile-settings')">
            <i class="fas fa-user-cog"></i>
            <span>个人设置</span>
          </a>
          <a href="#" class="user-menu-item">
            <i class="fas fa-chart-pie"></i>
            <span>学习统计</span>
          </a>
          <a href="#" class="user-menu-item">
            <i class="fas fa-bookmark"></i>
            <span>学习收藏</span>
          </a>
          <!--<a href="#" class="user-menu-item">
            <i class="fas fa-history"></i>
            <span>全部对话历史</span>
          </a>-->
          <a href="#" class="user-menu-item" @click="$emit('logout')">
            <i class="fas fa-sign-out-alt"></i>
            <span>退出登录</span>
          </a>
        </div>
      </div>
    </div>
  </div>
  </div>
</template>

<script>
export default {
  name: 'Sidebar',
  props: {
    isCollapsed: Boolean,
    isMobileOpen: Boolean,
    activeMenu: String,
    chatHistories: Array,
    activeHistoryIndex: Number,
    userInfo: Object,
    showUserCard: Boolean
  },
  computed: {
    userInitial() {
      return this.userInfo?.nickname?.charAt(0)?.toUpperCase() || 'U';
    }
  },
  methods: {
    handleToggleUserCard(event) {
      event.stopPropagation(); // 先阻止事件冒泡
      this.$emit('toggle-user-card',event); // 再触发事件
    }
  }
};
</script>

<style scoped>
.sidebar {
  width: 280px;
  background-color: white;
  box-shadow: var(--shadow);
  height: 100vh;
  display: flex;
  overflow: visible;
  flex-direction: column;
  height: 100vh;
  position: fixed;
  transition: width 0.3s ease;
  z-index: 10;
}

.sidebar.collapsed {
  width: 70px;
}

.sidebar.collapsed .logo h1,
.sidebar.collapsed .logo span,
.sidebar.collapsed .menu-item span,
.sidebar.collapsed .user-info,
.sidebar.collapsed .menu-section h3,
.sidebar.collapsed .chat-history,
.sidebar.collapsed .chat-history-item span {
  display: none;
}

.sidebar.collapsed .menu-item {
  justify-content: center;
  padding: 12px;
}

.sidebar.collapsed .menu-item i {
  margin-right: 0;
  font-size: 20px;
}

.sidebar.collapsed .user-profile {
  justify-content: center;
  padding: 15px 0;
}

.sidebar.collapsed .user-avatar {
  margin-right: 0;
  width: 40px;
  height: 40px;
}

.logo {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid var(--light-color);
  position: relative;
}

.logo h1 {
  font-size: 24px;
  color: var(--primary-color);
  transition: opacity 0.3s ease;
}

.logo span {
  font-size: 14px;
  color: var(--mid-gray);
  transition: opacity 0.3s ease;
}

.toggle-sidebar {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  cursor: pointer;
  color: var(--mid-gray);
  font-size: 16px;
}

.menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px 0;
  overflow-y: auto;
}

.menu-section {
  margin-bottom: 0px;
  flex-shrink: 0;
  position: relative;
}

.chat-history-container {
  max-height: 300px;
  overflow-y: auto;
  scrollbar-width: thin;
  -ms-overflow-style: none;
  padding-right: 8px; /* 添加右边距防止滚动条遮挡 */
  margin-left: 0; /* 重置左边距 */
}

.chat-history-container::-webkit-scrollbar {
  width: 6px;
  background-color: #f5f5f5;
}

.chat-history-container::-webkit-scrollbar-thumb {
  background-color: var(--primary-color);
  border-radius: 3px;
}

.chat-history {
  padding-top: 0;
}

.menu-section:first-child {
  flex-shrink: 0;
  overflow: hidden;
}

.menu-section:last-child {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.menu-section:last-child::-webkit-scrollbar {
  display: none;
}

.menu-section h3 {
  position: sticky;
  top: 0;
  background: white;
  padding: 10px 20px;
  margin: 0;
  z-index: 1;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  color: var(--mid-gray);
  text-transform: uppercase;
  letter-spacing: 1px;
  transition: opacity 0.3s ease;
}

.menu-item {
  padding: 12px 20px;
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
}

.menu-item:hover {
  background-color: rgba(67, 97, 238, 0.1);
}

.menu-item.active {
  background-color: rgba(67, 97, 238, 0.1);
  border-left: 4px solid var(--primary-color);
  color: var(--primary-color);
}

.menu-item.active i {
  color: var(--primary-color);
}

.menu-item i {
  margin-right: 12px;
  font-size: 18px;
  color: var(--mid-gray);
}

.menu-item span {
  font-size: 16px;
  transition: opacity 0.3s ease;
}

.chat-history {
  max-height: 300px;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.chat-history::-webkit-scrollbar {
  display: none;
}

.chat-history-item {
  padding: 10px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-left: 4px solid transparent;
  transition: all 0.2s;
}

.chat-history-item:hover {
  background-color: rgba(67, 97, 238, 0.1);
}

.chat-history-item.active {
  border-left-color: var(--primary-color);
  background-color: rgba(67, 97, 238, 0.1);
  color: var(--primary-color);
}

.history-content {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.history-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.chat-history-item:hover .history-actions {
  opacity: 1;
}

.action-btn {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: var(--mid-gray);
  transition: all 0.2s;
  border-radius: 4px;
}

.action-btn:hover {
  color: var(--primary-color);
  background-color: rgba(67, 97, 238, 0.1);
}

.history-title {
  font-weight: 500;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-time {
  font-size: 12px;
  color: var(--mid-gray);
}

.chat-history-item.active .history-actions {
  opacity: 1;
}

.no-history {
  padding: 40px 20px;
  text-align: center;
  color: var(--mid-gray);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 15px;
}

.no-history i.fa-comments {
  font-size: 32px;
  color: var(--light-color);
}

.no-history p {
  font-size: 14px;
  margin: 0;
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

.user-profile {
  padding: 15px 20px;
  display: flex;
  align-items: center;
  border-top: 1px solid var(--light-color);
  cursor: pointer;
  position: relative;
  background-color: white;
  transition: all 0.2s;
}

.user-profile:hover {
  background-color: rgba(67, 97, 238, 0.05);
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  margin-right: 12px;
  font-size: 16px;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--dark-color);
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-status {
  font-size: 12px;
  color: var(--mid-gray);
}

.user-card {
  position: absolute;
  bottom: 80px;
  left: 20px;
  width: 280px;
  background-color: white;
  border-radius: 12px;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
  z-index: 100;
  animation: slide-up 0.3s ease-out;
}

@keyframes slide-up {
  0% { transform: translateY(20px); opacity: 0; }
  100% { transform: translateY(0); opacity: 1; }
}

.user-card-header {
  padding: 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--light-color);
  position: relative;
}

.user-card-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background-color: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  margin-right: 15px;
  font-size: 24px;
}

.user-card-info {
  flex: 1;
  min-width: 0;
}

.user-card-info h3 {
  margin: 0;
  font-size: 18px;
  color: var(--dark-color);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-card-info p {
  margin: 0;
  font-size: 14px;
  color: var(--mid-gray);
  line-height: 1.4;
}

.user-card-close {
  position: absolute;
  top: 15px;
  right: 15px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--mid-gray);
  font-size: 16px;
  padding: 5px;
  border-radius: 50%;
  transition: all 0.2s;
}

.user-card-close:hover {
  background-color: rgba(0, 0, 0, 0.05);
  color: var(--dark-color);
}

.user-card-content {
  padding: 20px;
}

.user-stats {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 10px 0;
  border-bottom: 1px solid var(--light-color);
}

.stat-item {
  text-align: center;
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: var(--primary-color);
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--mid-gray);
}

.user-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: auto;
  width: auto;
}

.user-menu-item {
  padding: 12px 15px;
  display: flex;
  align-items: center;
  text-decoration: none;
  color: var(--dark-color);
  border-radius: 8px;
  transition: all 0.2s;
  height: 38px;
}

.user-menu-item:hover {
  background-color: rgba(67, 97, 238, 0.05);
}

.user-menu-item i {
  margin-right: 12px;
  width: 20px;
  text-align: center;
  color: var(--primary-color);
  font-size: 16px;
}

.sidebar.collapsed .user-profile {
  padding: 15px 0;
  justify-content: center;
}

.sidebar.collapsed .user-avatar {
  margin-right: 0;
}

.sidebar.collapsed .user-info {
  display: none;
}

@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
    position: fixed;
    height: 100%;
    z-index: 100;
  }

  .sidebar.active {
    transform: translateX(0);
    width: 280px;
  }

  .sidebar.active .logo h1,
  .sidebar.active .logo span,
  .sidebar.active .menu-item span,
  .sidebar.active .user-info,
  .sidebar.active .menu-section h3,
  .sidebar.active .chat-history,
  .sidebar.active .chat-history-item span {
    display: block;
  }

  .sidebar.active .menu-item {
    justify-content: flex-start;
    padding: 12px 20px;
  }

  .sidebar.active .menu-item i {
    margin-right: 12px;
    font-size: 18px;
  }

  .sidebar.active .user-profile {
    justify-content: flex-start;
    padding: 15px 20px;
  }

  .sidebar.active .user-avatar {
    margin-right: 12px;
  }

  .user-card {
    position: fixed;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    margin: 0;
    width: 90%;
    max-width: 320px;
  }
}
</style>