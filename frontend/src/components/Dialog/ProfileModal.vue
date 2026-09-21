<template>
  <div class="modal-overlay" @click.self="handleOverlayClick">
    <div class="profile-modal">
      <div class="modal-header">
        <h3>个人设置</h3>
        <button class="modal-close" @click="$emit('close')">
          <i class="fas fa-times"></i>
        </button>
      </div>

      <div class="modal-content">
        <div class="avatar-upload">
          <div class="avatar-preview" :style="{ backgroundImage: tempAvatarUrl ? `url(${tempAvatarUrl})` : 'none' }">
            <input
                type="file"
                ref="avatarInput"
                accept="image/*"
                @change="handleAvatarChange"
                style="display: none;"
            >
            <button class="avatar-edit-btn" @click="triggerAvatarUpload">
              <i class="fas fa-camera"></i>
            </button>
          </div>
          <p class="avatar-hint">点击头像可上传新图片</p>
        </div>

        <div class="form-group">
          <label for="nickname">昵称</label>
          <input
              type="text"
              id="nickname"
              v-model="localUserInfo.nickname"
              placeholder="请输入您的昵称"
          >
        </div>

        <div class="form-group">
          <label for="bio">个人简介</label>
          <textarea
              id="bio"
              v-model="localUserInfo.bio"
              placeholder="这个人好懒，什么也不写(￢︿̫̿￢☆)"
          ></textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="school">学校</label>
            <input
                type="text"
                id="school"
                v-model="localUserInfo.school"
                placeholder="请输入您的学校"
            >
          </div>

          <div class="form-group">
            <label for="major">专业</label>
            <input
                type="text"
                id="major"
                v-model="localUserInfo.major"
                placeholder="请输入您的专业"
            >
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn-cancel" @click="$emit('close')">取消</button>
        <button class="btn-save" @click="handleSave">保存更改</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ProfileModal',
  props: {
    userInfo: {
      type: Object,
      default: () => ({
        nickname: '',
        bio: '',
        school: '',
        major: '',
        avatarUrl: null
      })
    },
    tempAvatarUrl: {
      type: String,
      default: null
    }
  },
  data() {
    return {
      localUserInfo: JSON.parse(JSON.stringify(this.userInfo)), // 深拷贝避免响应式问题
      tempAvatarUrl: this.tempAvatarUrl, // 直接作为data属性
      avatarFile: null
    };
  },
  watch: {
    userInfo: {
      handler(newVal) {
        this.localUserInfo = { ...newVal };
      },
      deep: true,
      immediate: true
    }
  },
  methods: {
    handleOverlayClick(event) {
      // 确保事件对象被传递
      this.$emit('close', event);
    },
    triggerAvatarUpload() {
      this.$refs.avatarInput.click();
    },
    handleAvatarChange(event) {
      const file = event.target.files[0];
      if (!file) return;

      // 验证文件类型和大小
      const validTypes = ['image/jpeg', 'image/png', 'image/gif'];
      if (!validTypes.includes(file.type)) {
        this.$emit('error', '仅支持JPEG、PNG或GIF图片');
        return;
      }
      if (file.size > 2 * 1024 * 1024) {
        this.$emit('error', '图片大小不能超过2MB');
        return;
      }
      // 创建预览
      const reader = new FileReader();
      reader.onload = (e) => {
        this.tempAvatarUrl = e.target.result;
        // 2. 通知父组件更新临时显示（可选）
        this.$emit('avatar-preview', e.target.result);
        this.avatarFile = file;  // 确保保存文件对象
      };
      reader.onerror = (error) => {
        console.error('文件读取错误:', error);
        this.$emit('error', '图片读取失败');
      };
      reader.readAsDataURL(file);
    },
    handleSave() {
      this.$emit('save', {
        userInfo: this.localUserInfo,
        avatarFile: this.avatarFile
      });
    }
  }
};
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(5px);
  animation: fade-in 0.3s ease-out;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.profile-modal {
  background-color: white;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  animation: slide-up 0.3s ease-out;
}

@keyframes slide-up {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.modal-header {
  padding: 20px;
  border-bottom: 1px solid var(--light-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  font-size: 18px;
  color: var(--dark-color);
  margin: 0;
  font-weight: 600;
}

.modal-close {
  background: none;
  border: none;
  color: var(--mid-gray);
  font-size: 18px;
  cursor: pointer;
  transition: color 0.2s;
}

.modal-close:hover {
  color: var(--primary-color);
}

.modal-content {
  padding: 20px;
}

.avatar-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}

.avatar-preview {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background-color: var(--primary-color);
  background-size: cover;
  background-position: center;
  position: relative;
  margin-bottom: 10px;
  border: 3px solid white;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.avatar-edit-btn {
  position: absolute;
  bottom: 5px;
  right: 5px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--primary-color);
  color: white;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.avatar-edit-btn:hover {
  background-color: var(--secondary-color);
  transform: scale(1.1);
}

.avatar-hint {
  font-size: 12px;
  color: var(--mid-gray);
  margin: 0;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--dark-color);
  font-weight: 500;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 15px;
  border: 1px solid var(--light-color);
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--primary-color);
}

.form-group textarea {
  min-height: 80px;
  resize: vertical;
}

.form-row {
  display: flex;
  gap: 15px;
}

.form-row .form-group {
  flex: 1;
}

.modal-footer {
  padding: 15px 20px;
  border-top: 1px solid var(--light-color);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn-cancel,
.btn-save {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel {
  background-color: var(--light-color);
  color: var(--mid-gray);
  border: none;
}

.btn-cancel:hover {
  background-color: #e9ecef;
}

.btn-save {
  background-color: var(--primary-color);
  color: white;
  border: none;
}

.btn-save:hover {
  background-color: var(--secondary-color);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .profile-modal {
    width: 95%;
  }

  .form-row {
    flex-direction: column;
    gap: 15px;
  }
}
</style>