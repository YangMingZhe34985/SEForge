<template>
  <div class="code-quality-container">
    <div class="quality-header">
      <h2>代码质量评估</h2>
      <p>上传您的项目文件，获取详细的代码质量分析报告</p>
    </div>

    <div class="upload-section">
      <div class="upload-area"
           :class="{ 'drag-over': isDragOver }"
           @dragover.prevent="handleDragOver"
           @dragleave.prevent="handleDragLeave"
           @drop.prevent="handleDrop">
        <i class="fas fa-cloud-upload-alt"></i>
        <p>拖放项目ZIP文件到此处或</p>
        <input type="file" id="fileUpload" accept=".zip" @change="handleFileSelect" ref="fileInput" class="file-input">
        <button class="upload-btn" @click="triggerFileInput">选择文件</button>
        <p class="file-hint">支持 ZIP 格式，最大 50MB</p>
      </div>

      <div class="selected-file" v-if="selectedFile">
        <div class="file-info">
          <i class="fas fa-file-archive"></i>
          <div class="file-details">
            <span class="file-name">{{ selectedFile.name }}</span>
            <span class="file-size">{{ formatFileSize(selectedFile.size) }}</span>
          </div>
        </div>
        <button class="submit-btn" @click="submitFile" :disabled="isUploading">
          <span v-if="!isUploading">开始分析</span>
          <span v-else><i class="fas fa-spinner fa-spin"></i> 分析中...</span>
        </button>
      </div>
    </div>

    <div class="progress-container" v-if="isUploading">
      <div class="progress-bar">
        <div class="progress" :style="{ width: uploadProgress + '%' }"></div>
      </div>
      <span class="progress-text">{{ uploadProgress }}%</span>
    </div>

    <div class="reports-section" v-if="reports.length > 0">
      <h3>分析报告历史</h3>
      <div class="reports-list">
        <div class="report-card" v-for="(report, index) in reports" :key="index">
          <div class="report-header">
            <div class="report-title">{{ report.fileName }}</div>
            <div class="report-date">{{ report.date }}</div>
          </div>

          <!-- 根据数据可用性显示评分或简化显示 -->
          <div class="report-score" v-if="report.score !== 'N/A'">
            <div class="score-circle" :class="getScoreClass(report.score)">
              {{ report.score }}
            </div>
            <span>质量得分</span>
          </div>
          <div class="report-placeholder" v-else>
            <i class="fas fa-file-alt"></i>
            <span>报告已生成</span>
          </div>

          <!-- 按钮改为打开链接 -->
          <a :href="report.reportUrl" target="_blank" class="view-report-btn">
            查看完整报告
          </a>
        </div>
      </div>
    </div>

    <div class="empty-reports" v-else-if="!isUploading">
      <i class="fas fa-chart-bar"></i>
      <p>您还没有分析报告</p>
      <p class="empty-hint">上传项目文件开始您的第一次代码质量分析</p>
    </div>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'CodeQuality',
  data() {
    return {
      selectedFile: null,
      isDragOver: false,
      isUploading: false,
      uploadProgress: 0,
      reports: []
    }
  },
  methods: {
    handleDragOver(event) {
      this.isDragOver = true;
    },
    handleDragLeave(event) {
      this.isDragOver = false;
    },
    handleDrop(event) {
      this.isDragOver = false;
      const files = event.dataTransfer.files;
      if (files.length > 0) {
        const file = files[0];
        const validZipTypes = ['application/zip', 'application/x-zip-compressed', 'application/octet-stream'];
        const isZipExtension = file.name.toLowerCase().endsWith('.zip');
        if (validZipTypes.includes(file.type) || isZipExtension) {
          this.selectedFile = file;
        } else {
          this.showErrorMessage('请上传ZIP格式文件');
        }
      }
    },
    triggerFileInput() {
      this.$refs.fileInput.click();
    },
    handleFileSelect(event) {
      const file = event.target.files[0];
      if (file) {
        const validZipTypes = ['application/zip', 'application/x-zip-compressed', 'application/octet-stream'];
        const isZipExtension = file.name.toLowerCase().endsWith('.zip');
        if (validZipTypes.includes(file.type) || isZipExtension) {
          this.selectedFile = file;
        } else {
          this.showErrorMessage('请上传ZIP格式文件');
        }
      }
    },
    formatFileSize(size) {
      if (size < 1024) {
        return size + ' B';
      } else if (size < 1024 * 1024) {
        return (size / 1024).toFixed(2) + ' KB';
      } else {
        return (size / (1024 * 1024)).toFixed(2) + ' MB';
      }
    },
    submitFile() {
      if (!this.selectedFile) return;

      this.isUploading = true;
      this.uploadProgress = 0;

      const formData = new FormData();
      formData.append('file', this.selectedFile);

      axios.post('http://localhost:8080/code-quality/analyze/sonar', formData, {
        onUploadProgress: (progressEvent) => {
          this.uploadProgress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        }
      })
          .then(response => {
            this.isUploading = false;
            const resultText = response.data.data || '';
            const urlMatch = resultText.match(/https?:\/\/[^\s]+/);
            const reportUrl = urlMatch ? urlMatch[0] : '未能提取报告链接';

            const newReport = {
              id: Date.now().toString(),
              fileName: this.selectedFile.name,
              date: new Date().toLocaleString(),
              reportUrl: reportUrl,
              score: 'N/A',
              bugs: 'N/A',
              codeLines: 'N/A',
              complexity: 'N/A'
            };

            this.reports.unshift(newReport);
            this.selectedFile = null;
            this.showSuccessMessage('分析完成');
          })
          .catch(error => {
            this.isUploading = false;
            this.showErrorMessage('分析失败: ' + error.message);
          });
    },
    viewReport(reportId) {
      const report = this.reports.find(r => r.id === reportId);
      if (report && report.reportUrl) {
        window.open(report.reportUrl, '_blank');
      } else {
        this.showErrorMessage('未找到报告链接');
      }
    },
    getScoreClass(score) {
      if (score >= 90) return 'excellent';
      if (score >= 80) return 'good';
      if (score >= 70) return 'average';
      return 'poor';
    },
    showSuccessMessage(message) {
      this.$message.success(message);
    },
    showErrorMessage(message) {
      this.$message.error(message);
    }
  }
}
</script>


<style scoped>
.code-quality-container {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
}

.quality-header {
  margin-bottom: 30px;
  text-align: center;
}

.quality-header h2 {
  margin: 0;
  margin-bottom: 10px;
  color: var(--dark-color);
  font-size: 24px;
}

.quality-header p {
  margin: 0;
  color: var(--mid-gray);
  font-size: 16px;
}

.upload-section {
  margin-bottom: 30px;
}

.upload-area {
  border: 2px dashed var(--light-color);
  border-radius: 12px;
  padding: 40px;
  text-align: center;
  transition: all 0.3s ease;
  background-color: rgba(67, 97, 238, 0.02);
}

.upload-area.drag-over {
  background-color: rgba(67, 97, 238, 0.05);
  border-color: var(--primary-color);
}

.upload-area i {
  font-size: 48px;
  color: var(--primary-color);
  margin-bottom: 15px;
}

.upload-area p {
  margin: 10px 0;
  color: var(--mid-gray);
}

.file-input {
  display: none;
}

.report-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 20px 0;
  color: var(--mid-gray);
}

.report-placeholder i {
  font-size: 36px;
  margin-bottom: 10px;
  color: var(--primary-color);
}

.view-report-btn {
  width: 100%;
  background-color: transparent;
  border: 1px solid var(--primary-color);
  color: var(--primary-color);
  padding: 10px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.2s;
  text-align: center;
  text-decoration: none;
  display: block;
}

.view-report-btn:hover {
  background-color: var(--primary-color);
  color: white;
}

.upload-btn {
  background-color: var(--primary-color);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 16px;
  margin: 15px 0;
  transition: all 0.2s;
}

.upload-btn:hover {
  background-color: var(--secondary-color);
  transform: translateY(-2px);
}

.file-hint {
  font-size: 14px;
  color: var(--mid-gray);
}

.selected-file {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: var(--shadow);
}

.file-info {
  display: flex;
  align-items: center;
}

.file-info i {
  font-size: 24px;
  color: var(--primary-color);
  margin-right: 15px;
}

.file-details {
  display: flex;
  flex-direction: column;
}

.file-name {
  font-weight: 600;
  margin-bottom: 5px;
}

.file-size {
  font-size: 14px;
  color: var(--mid-gray);
}

.submit-btn {
  background-color: var(--primary-color);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background-color: var(--secondary-color);
  transform: translateY(-2px);
}

.submit-btn:disabled {
  background-color: var(--light-color);
  cursor: not-allowed;
}

.progress-container {
  margin: 20px 0;
}

.progress-bar {
  height: 8px;
  background-color: var(--light-color);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 5px;
}

.progress {
  height: 100%;
  background-color: var(--primary-color);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  color: var(--mid-gray);
}

.reports-section {
  margin-top: 40px;
}

.reports-section h3 {
  margin: 0 0 20px 0;
  color: var(--dark-color);
  font-size: 20px;
}

.reports-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.report-card {
  background-color: white;
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 20px;
  transition: all 0.3s ease;
}

.report-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.report-header {
  margin-bottom: 15px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.report-title {
  font-weight: 600;
  font-size: 16px;
  color: var(--dark-color);
  word-break: break-word;
}

.report-date {
  font-size: 14px;
  color: var(--mid-gray);
  white-space: nowrap;
  margin-left: 10px;
}

.report-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 20px 0;
}

.score-circle {
  width: 70px;
  height: 70px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 10px;
  color: white;
}

.score-circle.excellent {
  background-color: #4CAF50;
}

.score-circle.good {
  background-color: #8BC34A;
}

.score-circle.average {
  background-color: #FFC107;
}

.score-circle.poor {
  background-color: #FF5722;
}

.report-metrics {
  display: flex;
  justify-content: space-around;
  margin: 20px 0;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.metric i {
  font-size: 20px;
  color: var(--primary-color);
  margin-bottom: 5px;
}

.metric span {
  font-size: 14px;
  color: var(--mid-gray);
}

.view-report-btn {
  width: 100%;
  background-color: transparent;
  border: 1px solid var(--primary-color);
  color: var(--primary-color);
  padding: 10px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.2s;
}

.view-report-btn:hover {
  background-color: var(--primary-color);
  color: white;
}

.empty-reports {
  text-align: center;
  padding: 60px 0;
}

.empty-reports i {
  font-size: 60px;
  color: var(--light-color);
  margin-bottom: 20px;
}

.empty-reports p {
  margin: 10px 0;
  color: var(--mid-gray);
  font-size: 18px;
}

.empty-reports .empty-hint {
  font-size: 16px;
}

@media (max-width: 768px) {
  .code-quality-container {
    padding: 20px;
  }

  .upload-area {
    padding: 30px 15px;
  }

  .reports-list {
    grid-template-columns: 1fr;
  }

  .selected-file {
    flex-direction: column;
    gap: 15px;
  }

  .file-info {
    width: 100%;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>