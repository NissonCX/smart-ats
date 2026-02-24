import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Form, Input, Button, message, Tabs, Select, Row, Col } from 'antd';
import {
  UserOutlined,
  LockOutlined,
  MailOutlined,
  SafetyOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { authApi } from '../../api';
import { useAuthStore } from '../../store/auth';
import type { LoginRequest, RegisterRequest } from '../../types';

// Check for reduced motion preference
const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const getTransition = () => ({
  type: 'spring' as const,
  stiffness: 200,
  damping: 30,
});

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);
  const [loading, setLoading] = useState(false);
  const [codeLoading, setCodeLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [activeTab, setActiveTab] = useState('login');

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/dashboard';

  const handleLogin = async (values: LoginRequest) => {
    setLoading(true);
    try {
      const { data } = await authApi.login(values);
      const res = data.data!;
      login(res.accessToken, res.refreshToken, res.userInfo);
      message.success(`欢迎回来，${res.userInfo.username}`);
      navigate(from, { replace: true });
    } catch {
      // interceptor handles error
    } finally {
      setLoading(false);
    }
  };

  const [registerForm] = Form.useForm();
  const handleSendCode = async () => {
    const email = registerForm.getFieldValue('email');
    if (!email) {
      message.warning('请先输入邮箱');
      return;
    }
    setCodeLoading(true);
    try {
      await authApi.sendVerificationCode({ email });
      message.success('验证码已发送');
      setCountdown(60);
      const timer = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(timer);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } catch {
      /* handled */
    } finally {
      setCodeLoading(false);
    }
  };

  const handleRegister = async (values: RegisterRequest) => {
    setLoading(true);
    try {
      await authApi.register(values);
      message.success('注册成功，请登录');
      setActiveTab('login');
    } catch {
      /* handled */
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex bg-white">
      {/* Left — Brand */}
      <motion.div
        initial={{ opacity: 0, x: prefersReducedMotion() ? 0 : -24 }}
        animate={{ opacity: 1, x: 0 }}
        transition={getTransition()}
        className="flex-1 hidden lg:flex flex-col justify-center px-12 xl:px-20 relative overflow-hidden"
      >
        {/* Subtle gradient orb */}
        <div className="absolute top-1/4 -left-32 w-[480px] h-[480px] rounded-full opacity-60 blur-[120px] bg-cyan-500/10" />
        <div className="absolute bottom-1/4 right-0 w-[320px] h-[320px] rounded-full opacity-40 blur-[100px] bg-violet-500/10" />

        <div className="relative z-10">
          {/* Logo */}
          <div className="w-12 h-12 rounded-xl bg-gray-900 flex items-center justify-center mb-10">
            <span className="text-white text-lg font-bold tracking-tight">S</span>
          </div>

          <h1 className="text-4xl xl:text-[42px] font-bold text-gray-900 tracking-tight leading-tight mb-4">
            SmartATS
          </h1>
          <p className="text-xl text-gray-600 font-light leading-relaxed mb-3">
            智能招聘管理系统
          </p>
          <p className="text-gray-500 leading-relaxed max-w-md text-sm">
            AI 驱动的简历解析 · RAG 语义候选人搜索 · 完整招聘流程管理
          </p>

          {/* Stats */}
          <div className="flex gap-12 mt-16">
            {[
              { num: '40+', label: 'API 接口' },
              { num: 'AI', label: '智能解析' },
              { num: 'RAG', label: '语义搜索' },
            ].map((item, i) => (
              <motion.div
                key={item.label}
                initial={{ opacity: 0, y: prefersReducedMotion() ? 0 : 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: prefersReducedMotion() ? 0 : 0.3 + i * 0.1, ...getTransition() }}
              >
                <div className="text-2xl font-bold text-gray-900">{item.num}</div>
                <div className="text-sm text-gray-500 mt-1">{item.label}</div>
              </motion.div>
            ))}
          </div>
        </div>
      </motion.div>

      {/* Right — Form */}
      <motion.div
        initial={{ opacity: 0, x: prefersReducedMotion() ? 0 : 24 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ ...getTransition(), delay: prefersReducedMotion() ? 0 : 0.1 }}
        className="w-full lg:w-[480px] flex items-center justify-center p-4 sm:p-6 lg:p-12"
      >
        <div className="w-full max-w-[380px]">
          {/* Mobile logo */}
          <div className="lg:hidden flex items-center gap-3 mb-10">
            <div className="w-10 h-10 rounded-lg bg-gray-900 flex items-center justify-center">
              <span className="text-white font-bold">S</span>
            </div>
            <span className="text-xl font-semibold text-gray-900 tracking-tight">SmartATS</span>
          </div>

          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            centered
            items={[
              {
                key: 'login',
                label: '登录',
                children: (
                  <Form
                    size="large"
                    onFinish={handleLogin}
                    autoComplete="off"
                    className="mt-6"
                  >
                    <Form.Item
                      name="username"
                      rules={[{ required: true, message: '请输入用户名' }]}
                    >
                      <Input
                        prefix={<UserOutlined className="text-gray-400" />}
                        placeholder="用户名"
                      />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      rules={[{ required: true, message: '请输入密码' }]}
                    >
                      <Input.Password
                        prefix={<LockOutlined className="text-gray-400" />}
                        placeholder="密码"
                      />
                    </Form.Item>
                    <Form.Item className="mt-8">
                      <Button
                        type="primary"
                        htmlType="submit"
                        block
                        loading={loading}
                        className="h-12 text-sm"
                      >
                        登 录
                        <ArrowRightOutlined className="ml-1" />
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
              {
                key: 'register',
                label: '注册',
                children: (
                  <Form
                    form={registerForm}
                    size="large"
                    onFinish={handleRegister}
                    autoComplete="off"
                    className="mt-6"
                    initialValues={{ role: 'HR' }}
                  >
                    <Form.Item
                      name="username"
                      rules={[
                        { required: true, message: '请输入用户名' },
                        { min: 4, max: 20, message: '4-20个字符' },
                      ]}
                    >
                      <Input
                        prefix={<UserOutlined className="text-gray-400" />}
                        placeholder="用户名"
                      />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      rules={[
                        { required: true, message: '请输入密码' },
                        { min: 6, max: 20, message: '6-20个字符' },
                      ]}
                    >
                      <Input.Password
                        prefix={<LockOutlined className="text-gray-400" />}
                        placeholder="密码"
                      />
                    </Form.Item>
                    <Form.Item
                      name="email"
                      rules={[
                        { required: true, message: '请输入邮箱' },
                        { type: 'email', message: '邮箱格式不正确' },
                      ]}
                    >
                      <Input
                        prefix={<MailOutlined className="text-gray-400" />}
                        placeholder="邮箱"
                      />
                    </Form.Item>
                    <Form.Item name="role" label="角色">
                      <Select
                        options={[
                          { value: 'HR', label: 'HR' },
                          { value: 'INTERVIEWER', label: '面试官' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item
                      name="verificationCode"
                      rules={[
                        { required: true, message: '请输入验证码' },
                        { pattern: /^\d{6}$/, message: '6位数字验证码' },
                      ]}
                    >
                      <Row gutter={8}>
                        <Col flex="auto">
                          <Input
                            prefix={<SafetyOutlined className="text-gray-400" />}
                            placeholder="验证码"
                            maxLength={6}
                          />
                        </Col>
                        <Col>
                          <Button
                            onClick={handleSendCode}
                            loading={codeLoading}
                            disabled={countdown > 0}
                          >
                            {countdown > 0 ? `${countdown}s` : '获取验证码'}
                          </Button>
                        </Col>
                      </Row>
                    </Form.Item>
                    <Form.Item className="mt-6">
                      <Button
                        type="primary"
                        htmlType="submit"
                        block
                        loading={loading}
                        className="h-12 text-sm"
                      >
                        注 册
                        <ArrowRightOutlined className="ml-1" />
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
            ]}
          />

          <p className="text-center mt-10 text-xs text-gray-400 tracking-wide">
            SmartATS · 智能招聘管理系统
          </p>
        </div>
      </motion.div>
    </div>
  );
}
