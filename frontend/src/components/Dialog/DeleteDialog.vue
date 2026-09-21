<template>
  <div class="dialog-overlay">
    <div class="delete-dialog" @click.stop>
      <div class="dialog-header">
        <h3>确认删除</h3>
        <button class="dialog-close" @click="$emit('cancel')">
          <i class="fas fa-times"></i>
        </button>
      </div>
      <div class="dialog-content">
        <div class="warning-icon">
          <i class="fas fa-exclamation-triangle"></i>
        </div>
        <p>确定要删除这个会话吗？此操作不可恢复。</p>
      </div>
      <div class="dialog-footer">
        <button class="btn-cancel" @click="$emit('cancel')">取消</button>
        <button class="btn-confirm" @click="$emit('confirm')">
          <i class="fas fa-trash-alt"></i>
          确定删除
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DeleteDialog',
  emits: ['confirm', 'cancel']
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

.delete-dialog {
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
  padding: 25px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.warning-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background-color: #fff8e6;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 15px;
}

.warning-icon i {
  font-size: 28px;
  color: #ffc107;
}

.dialog-content p {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
  color: var(--dark-color);
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
  background-color: #dc3545;
  color: white;
  border: none;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-confirm:hover {
  background-color: #c82333;
  transform: translateY(-1px);
}

.btn-confirm i {
  font-size: 14px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .delete-dialog {
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

  .warning-icon {
    width: 50px;
    height: 50px;
  }
}
</style>