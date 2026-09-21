<template>
  <!-- 最外层的大盒子 -->
  <div class="bigBox">
    <div class="box">
      <!-- 滑动盒子 -->
      <div class="pre-box" ref="preRef">
        <h1>WELCOME</h1>
        <p>JOIN US!</p>
        <div class="img-box">
          <img :src="flag==true?imgList[1]:imgList[0]" alt="" />
        </div>
      </div>

      <!-- 错误信息提示 -->
      <transition name="fade">
        <div v-if="errorMsg" class="error-message">
          {{ errorMsg }}
        </div>
      </transition>

      <!-- 注册盒子 -->
      <div class="register-form">
        <!-- 标题盒子 -->
        <div class="title-box">
          <h1>注册</h1>
        </div>
        <!-- 输入框盒子 -->
        <div class="input-box">
          <input v-model="registerForm.username" type="text" placeholder="用户名" @focus="clearError">
          <input v-model="registerForm.password" type="password" placeholder="密码" @focus="clearError">
          <input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" @focus="clearError">
        </div>
        <!-- 按钮盒子 -->
        <div class="btn-box">
          <button @click="handleRegister" :disabled="loading">
            {{ loading ? '注册中...' : '注册' }}
          </button>
          <p @click="switchForm">已有账号?去登录</p>
        </div>
      </div>

      <!-- 登录盒子 -->
      <div class="login-form">
        <!-- 标题盒子 -->
        <div class="title-box">
          <h1>登录</h1>
        </div>
        <!-- 输入框盒子 -->
        <div class="input-box">
          <input v-model="loginForm.username" type="text" placeholder="用户名" @focus="clearError">
          <input v-model="loginForm.password" type="password" placeholder="密码" @focus="clearError">
        </div>
        <!-- 按钮盒子 -->
        <div class="btn-box">
          <button @click="handleLogin" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
          </button>
          <p @click="switchForm">没有账号?去注册</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import avatar from '@/assets/img/avatar.jpg'
import avatar1 from '@/assets/img/avatar1.jpg'
import md5 from 'js-md5'

const router = useRouter()
const preRef = ref(null)
const imgList = ref([avatar, avatar1])
const flag = ref(true)
const loading = ref(false)
const errorMsg = ref('')

// 注册表单数据
const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: ''
})

// 登录表单数据
const loginForm = ref({
  username: '',
  password: ''
})

// 切换表单
const switchForm = () => {
  clearForm()
  clearError()
  mySwitch()
}

// 清空表单
const clearForm = () => {
  registerForm.value = {
    username: '',
    password: '',
    confirmPassword: ''
  }
  loginForm.value = {
    username: '',
    password: ''
  }
}

// 清空错误信息
const clearError = () => {
  errorMsg.value = ''
}

// 滑动动画
const mySwitch = () => {
  if (flag.value) {
    preRef.value.style.background = '#c2e9fb'
    preRef.value.style.transform = 'translateX(100%)'
  } else {
    preRef.value.style.background = '#a1c4fd'
    preRef.value.style.transform = 'translateX(0%)'
  }
  flag.value = !flag.value
}

// 显示错误信息（3秒后自动消失）
const showError = (message) => {
  errorMsg.value = message
  setTimeout(() => {
    if (errorMsg.value === message) {
      errorMsg.value = ''
    }
  }, 3000)
}

// 注册方法
const handleRegister = async () => {
  if (loading.value) return
  loading.value = true

  try {
    // 前端验证
    if (!registerForm.value.username || !registerForm.value.password) {
      throw new Error('用户名和密码不能为空')
    }

    if (registerForm.value.password !== registerForm.value.confirmPassword) {
      throw new Error('两次输入的密码不一致')
    }

    // 密码复杂度验证
    const passwordRegex = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,}$/
    if (!passwordRegex.test(registerForm.value.password)) {
      throw new Error('密码至少6位且包含字母和数字')
    }

    // 发送注册请求
    const response = await axios.post('http://localhost:8080/api/user/register', {
      username: registerForm.value.username,
      password: registerForm.value.password
    })

    if (response.data.code === 200) {
      // 注册成功后自动切换到登录界面
      showError('注册成功！请登录')
      switchForm()
    } else {
      throw new Error(response.data.message || '注册失败')
    }
  } catch (error) {
    console.error('注册错误:', error)
    showError(error.response?.data?.message || error.message || '注册失败，请重试')
  } finally {
    loading.value = false
  }
}

// 登录方法
const handleLogin = async () => {
  if (loading.value) return
  loading.value = true

  try {
    const response = await axios.post('http://localhost:8080/api/user/login', {
      username: loginForm.value.username,
      password: loginForm.value.password
    });

    if (response.data.code === 200) {
      const { userId, username } = response.data.data;
      console.log("登录成功，用户ID:", userId);
      
      // 存储必要的信息
      localStorage.setItem("userId", userId);
      localStorage.setItem("userToken", response.data.data.token || 'dummy-token'); // 如果后端返回token的话
      localStorage.setItem("userInfo", JSON.stringify(response.data.data));
      
      // 显示成功消息
      showError('登录成功！正在跳转...');
      
      // 延迟跳转以显示成功消息
      setTimeout(() => {
        router.push('/function');
      }, 1000);
    } else {
      throw new Error(response.data.message || '登录失败');
    }
  } catch (error) {
    console.error('登录错误:', error);
    showError(error.response?.data?.message || error.message || '登录失败，请重试');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
/* 去除input的轮廓 */
input {
  outline: none;
}

html,

.bigBox {
  /* 溢出隐藏 */
  height:100vh;
  overflow-x: hidden;
  display: flex;
  /* 渐变方向从左到右 */
  background:linear-gradient(220.55deg, #7CF7FF 0%, #4B73FF 100%);
}

span {
  position: absolute;
  z-index: 0;
  bottom: 0;
  border-radius: 50%;
  /* 径向渐变 */
  background: radial-gradient(circle at 72% 28%, #fff 3px, #ff7edf 8%, #5b5b5b, #aad7f9 100%);
  /* 泡泡内阴影 */
  box-shadow: inset 0 0 6px #fff,
  inset 3px 0 6px #eaf5fc,
  inset 2px -2px 10px #efcde6,
  inset 0 0 60px #f9f6de,
  0 0 20px #fff;
  /* 动画 */
  animation: myMove 4s linear infinite;
}


@keyframes myMove {
  0% {
    transform: translateY(0%);
    opacity: 1;
  }

  50% {
    transform: translate(10%, -1000%);
  }

  75% {
    transform: translate(-20%, -1200%);
  }

  99% {
    opacity: .9;
  }

  100% {
    transform: translateY(-1800%) scale(1.5);
    opacity: 0;
  }
}

/* 最外层的大盒子 */
.box {
  width: 1050px;
  height: 600px;
  display: flex;
  /* 相对定位 */
  position: relative;
  z-index: 2;
  margin: auto;
  /* 设置圆角 */
  border-radius: 8px;
  /* 设置边框 */
  border: 1px solid rgba(255, 255, 255, .6);
  /* 设置盒子阴影 */
  box-shadow: 2px 1px 19px rgba(0, 0, 0, .1);
}

/* 滑动的盒子 */
.pre-box {
  /* 宽度为大盒子的一半 */
  width: 50%;
  /* width: var(--width); */
  height: 100%;
  /* 绝对定位 */
  position: absolute;
  /* 距离大盒子左侧为0 */
  left: 0;
  /* 距离大盒子顶部为0 */
  top: 0;
  z-index: 99;
  border-radius: 4px;
  background-color: #a1c4fd;
  &.login-mode {
    background-color: #3f37c9;
  }
  box-shadow: 2px 1px 19px rgba(0, 0, 0, .1);
  /* 动画过渡，先加速再减速 */
  transition: 0.5s ease-in-out;
}

/* 滑动盒子的标题 */
.pre-box h1 {
  margin-top: 150px;
  text-align: center;
  /* 文字间距 */
  letter-spacing: 5px;
  color: white;
  /* 禁止选中 */
  user-select: none;
  /* 文字阴影 */
  text-shadow: 4px 4px 3px rgba(0, 0, 0, .1);
}

/* 滑动盒子的文字 */
.pre-box p {
  height: 30px;
  line-height: 30px;
  text-align: center;
  margin: 20px 0;
  /* 禁止选中 */
  user-select: none;
  font-weight: bold;
  color: white;
  text-shadow: 4px 4px 3px rgba(0, 0, 0, .1);
}

/* 图片盒子 */
.img-box {
  width: 200px;
  height: 200px;
  margin: 20px auto;
  /* 设置为圆形 */
  border-radius: 50%;
  /* 设置用户禁止选中 */
  user-select: none;
  overflow: hidden;
  box-shadow: 4px 4px 3px rgba(0, 0, 0, .1);
}

/* 图片 */
.img-box img {
  width: 100%;
  transition: 0.5s;
}

/* 登录和注册盒子 */
.login-form,
.register-form {
  flex: 1;
  height: 100%;
}

/* 标题盒子 */
.title-box {
  height: 300px;
  line-height: 500px;

}

/* 标题 */
.title-box h1 {
  text-align: center;
  color: white;
  /* 禁止选中 */
  user-select: none;
  letter-spacing: 5px;
  text-shadow: 4px 4px 3px rgba(0, 0, 0, .1);

}

/* 输入框盒子 */
.input-box {
  display: flex;
  /* 纵向布局 */
  flex-direction: column;
  /* 水平居中 */
  align-items: center;
}

/* 输入框 */
input {
  width: 60%;
  height: 40px;
  margin-bottom: 20px;
  text-indent: 10px;
  border: 1px solid #fff;
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 120px;
  /* 增加磨砂质感*/
  backdrop-filter: blur(10px);
}

input:focus {
  /* 光标颜色 */
  color: black;

}

/* 聚焦时隐藏文字 */
input:focus::placeholder {
  opacity: 0;
}

/* 按钮盒子 */
.btn-box {
  display: flex;
  justify-content: center;
}

/* 按钮 */
button {
  width: 100px;
  height: 30px;
  margin: 0 7px;
  line-height: 30px;
  border: none;
  border-radius: 4px;
  background-color: var(--primary-color);
  &:hover {
    background-color: var(--secondary-color);
  }
  color: white;
}

/* 按钮悬停时 */
button:hover {
  /* 鼠标小手 */
  cursor: pointer;
  /* 透明度 */
  opacity: .8;
}

/* 按钮文字 */
.btn-box p {
  height: 30px;
  line-height: 30px;
  /* 禁止选中*/
  user-select: none;
  font-size: 14px;
  color: white;

}

.btn-box p:hover {
  cursor: pointer;
  border-bottom: 1px solid white;
}

.error-message {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  background-color: #ffebee;
  color: #c62828;
  border-radius: 4px;
  z-index: 100;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.5s;
}
.fade-enter, .fade-leave-to {
  opacity: 0;
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
</style>