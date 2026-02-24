import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Form,
  Input,
  Button,
  Typography,
  message,
  Tabs,
  Select,
  Row,
  Col,
} from 'antd';
import {
  UserOutlined,
  LockOutlined,
  MailOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import { authApi } from '../../api';
import { useAuthStore } from '../../store/auth';
import type { LoginRequest, RegisterRequest } from '../../types';

const { Title, Text, Paragraph } = Typography;

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);
  const [loading, setLoading] = useState(false);
  const [codeLoading, setCodeLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [activeTab, setActiveTab] = useState('login');

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/dashboard';

  // ==================== 登录 ====================
  const handleLogin = async (values: LoginRequest) => {
    setLoading(true);
    try {
      const { data } = await authApi.login(values);
      const res = data.data!;
      login(res.accessToken, res.refreshToken, res.userInfo);
      message.success(`欢迎回来，${res.userInfo.username}！`);
      navigate(from, { replace: true });
    } catch {
      // 拦截器已处理错误提示
    } finally {
      setLoading(false);
    }
  };

  // ==================== 发送验证码 ====================
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
      // handled
    } finally {
      setCodeLoading(false);
    }
  };

  // ==================== 注册 ====================
  const handleRegister = async (values: RegisterRequest) => {
    setLoading(true);
    try {
      await authApi.register(values);
      message.success('注册成功，请登录');
      setActiveTab('login');
    } catch {
      // handled
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      }}
    >
      {/* 左侧品牌区 */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          padding: '0 80px',
          color: '#fff',
        }}
      >
        <div
          style={{
            width: 56,
            height: 56,
            borderRadius: 16,
            background: 'rgba(255,255,255,0.2)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            fontSize: 24,
            marginBottom: 32,
          }}
        >
          S
        </div>
        <Title level={1} style={{ color: '#fff', marginBottom: 16, fontSize: 42 }}>
          SmartATS
        </Title>
        <Title level={3} style={{ color: 'rgba(255,255,255,0.85)', fontWeight: 400 }}>
          智能招聘管理系统
        </Title>
        <Paragraph
          style={{
            color: 'rgba(255,255,255,0.7)',
            fontSize: 16,
            lineHeight: 1.8,
            marginTop: 24,
            maxWidth: 440,
          }}
        >
          AI 驱动的简历解析 · RAG 语义候选人搜索 · 完整招聘流程管理
          <br />
          让招聘更智能、更高效
        </Paragraph>

        <div style={{ display: 'flex', gap: 32, marginTop: 48 }}>
          {[
            { num: '40+', label: 'API 接口' },
            { num: 'AI', label: '智能解析' },
            { num: 'RAG', label: '语义搜索' },
          ].map((item) => (
            <div key={item.label}>
              <div style={{ fontSize: 28, fontWeight: 700 }}>{item.num}</div>
              <div style={{ fontSize: 14, opacity: 0.7, marginTop: 4 }}>{item.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* 右侧表单区 */}
      <div
        style={{
          width: 480,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 40,
        }}
      >
        <div
          style={{
            width: '100%',
            maxWidth: 400,
            background: '#fff',
            borderRadius: 20,
            padding: '40px 36px',
            boxShadow: '0 20px 60px rgba(0,0,0,0.15)',
          }}
        >
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
                    style={{ marginTop: 8 }}
                  >
                    <Form.Item
                      name="username"
                      rules={[{ required: true, message: '请输入用户名' }]}
                    >
                      <Input prefix={<UserOutlined />} placeholder="用户名" />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      rules={[{ required: true, message: '请输入密码' }]}
                    >
                      <Input.Password prefix={<LockOutlined />} placeholder="密码" />
                    </Form.Item>
                    <Form.Item>
                      <Button type="primary" htmlType="submit" block loading={loading}>
                        登 录
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
                    style={{ marginTop: 8 }}
                    initialValues={{ role: 'HR' }}
                  >
                    <Form.Item
                      name="username"
                      rules={[
                        { required: true, message: '请输入用户名' },
                        { min: 4, max: 20, message: '4-20个字符' },
                      ]}
                    >
                      <Input prefix={<UserOutlined />} placeholder="用户名" />
                    </Form.Item>
                    <Form.Item
                      name="password"
                      rules={[
                        { required: true, message: '请输入密码' },
                        { min: 6, max: 20, message: '6-20个字符' },
                      ]}
                    >
                      <Input.Password prefix={<LockOutlined />} placeholder="密码" />
                    </Form.Item>
                    <Form.Item
                      name="email"
                      rules={[
                        { required: true, message: '请输入邮箱' },
                        { type: 'email', message: '邮箱格式不正确' },
                      ]}
                    >
                      <Input prefix={<MailOutlined />} placeholder="邮箱" />
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
                          <Input prefix={<SafetyOutlined />} placeholder="验证码" maxLength={6} />
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
                    <Form.Item>
                      <Button type="primary" htmlType="submit" block loading={loading}>
                        注 册
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
            ]}
          />

          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              SmartATS · 智能招聘管理系统
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
}
