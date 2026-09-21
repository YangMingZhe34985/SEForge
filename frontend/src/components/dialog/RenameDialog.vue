<template>
  <div class="dialog-overlay">
    <div class="rename-dialog" @click.stop>
      <div class="dialog-header">
        <h3>重命名会话</h3>
        <button class="dialog-close" @click="$emit('cancel')">
          <i class="fas fa-times"></i>
        </button>
      </div>
      <div class="dialog-content">
        <div class="input-group">
          <label for="sessionTitle">会话名称</label>
          <input
              id="sessionTitle"
              ref="titleInput"
              type="text"
              :value="currentTitle"
              @input="$emit('update-title', $event.target.value)"
              @keyup.enter="$emit('confirm')"
              @keyup.esc="$emit('cancel')"
              placeholder="请输入新的会话名称"
          >
        </div>
      </div>
      <div class="dialog-footer">
        <button class="btn-cancel" @click="$emit('cancel')">取消</button>
        <button class="btn-confirm" @click="$emit('confirm')">确定</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'RenameDialog',
  props: {
    currentTitle: {
      type: String,
      default: ''
    }
  },
  emits: ['confirm', 'cancel', 'update-title'],
  mounted() {
    this.$nextTick(() => {
      this.$refs.titleInput.focus();
      this.$refs.titleInput.select();
    });
  }
};
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fade-in 0.3s ease-out;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.rename-dialog {
  background: white;
  width: 400px;
  max-width: 90%;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  animation: dialog-pop 0.3s ease-out;
}

@keyframes dialog-pop {
  0% { transform: scale(0.9); opacity: 0; }
  100% { transform: scale(1); opacity: 1; }
}

.dialog-header {
  padding: 20px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--dark-color);
  font-weight: 600;
}

.dialog-close {
  background: none;
  border: none;
  padding: 8px;
  cursor: pointer;
  color: var(--mid-gray);
  border-radius: 6px;
  transition: all 0.2s;
}

.dialog-close:hover {
  background-color: #f5f5f5;
  color: var(--dark-color);
}

.dialog-content {
  padding: 20px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-group label {
  font-size: 14px;
  color: var(--mid-gray);
  font-weight: 500;
}

.input-group input {
  padding: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s;
}

.input-group input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.1);
}

.dialog-footer {
  padding: 20px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-cancel, .btn-confirm {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel {
  background-color: #f5f5f5;
  border: none;
  color: var(--mid-gray);
}

.btn-cancel:hover {
  background-color: #ebebeb;
  color: var(--dark-color);
}

.btn-confirm {
  background-color: var(--primary-color);
  border: none;
  color: white;
}

.btn-confirm:hover {
  background-color: var(--secondary-color);
  transform: translateY(-1px);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .rename-dialog {
    width: 90%;
    margin: 20px;
  }

  .dialog-header {
    padding: 15px;
  }

  .dialog-content {
    padding: 15px;
  }

  .dialog-footer {
    padding: 15px;
  }
}
</style>