import { useEffect, useState } from 'react';
import { Card, Statistic, List, Tag, Space, Typography } from 'antd';
import {
  FileTextOutlined,
  TeamOutlined,
  SolutionOutlined,
  RiseOutlined,
  EyeOutlined,
  CloudUploadOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { jobApi, applicationApi, candidateApi } from '../../api';
import { useAuthStore } from '../../store/auth';
import type { JobResponse, ApplicationResponse } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;

const statusColorMap: Record<string, string> = {
  PENDING: 'default',
  SCREENING: 'processing',
  INTERVIEW: 'warning',
  OFFER: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'default',
};

export default function DashboardPage() {
  const userInfo = useAuthStore((s) => s.userInfo);
  const navigate = useNavigate();
  const [hotJobs, setHotJobs] = useState<JobResponse[]>([]);
  const [recentApps, setRecentApps] = useState<ApplicationResponse[]>([]);
  const [stats, setStats] = useState({ jobs: 0, candidates: 0, applications: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDashboard();
  }, []);

  const loadDashboard = async () => {
    setLoading(true);
    try {
      const [jobsRes, hotRes, appsRes, candidatesRes] = await Promise.all([
        jobApi.list({ pageNum: 1, pageSize: 1 }),
        jobApi.hot(5),
        applicationApi.list({ pageNum: 1, pageSize: 5 }),
        candidateApi.list({ page: 1, pageSize: 1 }),
      ]);
      setStats({
        jobs: jobsRes.data.data?.total || 0,
        candidates: candidatesRes.data.data?.total || 0,
        applications: appsRes.data.data?.total || 0,
      });
      setHotJobs(hotRes.data.data || []);
      setRecentApps(appsRes.data.data?.records || []);
    } catch {
      /* handled */
    } finally {
      setLoading(false);
    }
  };

  const greetingTime = () => {
    const h = new Date().getHours();
    if (h < 6) return '凌晨好';
    if (h < 12) return '早上好';
    if (h < 14) return '中午好';
    if (h < 18) return '下午好';
    return '晚上好';
  };

  const statCards = [
    {
      title: '职位总数',
      value: stats.jobs,
      icon: <FileTextOutlined />,
      color: 'text-cyan-600',
      bg: 'bg-cyan-50',
    },
    {
      title: '候选人',
      value: stats.candidates,
      icon: <TeamOutlined />,
      color: 'text-emerald-600',
      bg: 'bg-emerald-50',
    },
    {
      title: '申请数',
      value: stats.applications,
      icon: <SolutionOutlined />,
      color: 'text-amber-600',
      bg: 'bg-amber-50',
    },
    {
      title: 'AI 额度',
      value: `${userInfo?.todayAiUsed || 0}/${userInfo?.dailyAiQuota || 0}`,
      icon: <RiseOutlined />,
      color: 'text-violet-600',
      bg: 'bg-violet-50',
    },
  ];

  const quickActions = [
    { icon: <FileTextOutlined />, label: '发布职位', desc: '创建新职位', path: '/jobs' },
    { icon: <CloudUploadOutlined />, label: '上传简历', desc: 'AI 解析信息', path: '/resumes' },
    { icon: <SearchOutlined />, label: '智能搜索', desc: 'RAG 语义匹配', path: '/candidates/search' },
    { icon: <SettingOutlined />, label: '系统设置', desc: 'Webhook 配置', path: '/settings' },
  ];

  return (
    <PageTransition>
      {/* Welcome Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">
          {greetingTime()}，{userInfo?.username}
        </h1>
        <p className="text-gray-500 mt-1">这是你的招聘工作台，实时掌控招聘进展</p>
      </div>

      {/* Stat cards */}
      <motion.div
        variants={staggerContainer}
        initial="initial"
        animate="animate"
        className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8"
      >
        {statCards.map((item) => (
          <motion.div key={item.title} variants={staggerItem}>
            <Card
              bordered={false}
              className="!border-0 !shadow-sm hover:!shadow-md transition-shadow"
              styles={{ body: { padding: '20px' } }}
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-gray-500 text-sm mb-2">{item.title}</p>
                  <div className="text-3xl font-bold text-gray-900">
                    {typeof item.value === 'number' ? (
                      <Statistic
                        value={item.value}
                        valueStyle={{
                          fontSize: '28px',
                          fontWeight: 700,
                          color: '#111827',
                          lineHeight: 1,
                        }}
                      />
                    ) : (
                      item.value
                    )}
                  </div>
                </div>
                <div className={`w-14 h-14 rounded-xl ${item.bg} flex items-center justify-center text-2xl ${item.color}`}>
                  {item.icon}
                </div>
              </div>
            </Card>
          </motion.div>
        ))}
      </motion.div>

      {/* Content grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Hot jobs */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, type: 'spring', stiffness: 200, damping: 30 }}
        >
          <Card
            bordered={false}
            className="!border-0 !shadow-sm h-full"
            title={<span className="font-medium text-gray-700">热门职位</span>}
            loading={loading}
          >
            <List
              dataSource={hotJobs}
              renderItem={(job) => (
                <List.Item
                  className="!border-b-0"
                  extra={
                    <Space size={4}>
                      <EyeOutlined className="text-gray-400 text-xs" />
                      <span className="text-gray-400 text-sm">{job.viewCount}</span>
                    </Space>
                  }
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        <span className="font-medium text-gray-900">{job.title}</span>
                        <Tag className="!text-xs !bg-cyan-50 !text-cyan-600 !border-cyan-100">
                          {job.salaryRange}
                        </Tag>
                      </Space>
                    }
                    description={
                      <span className="text-gray-400 text-sm">
                        {job.department || '-'} · {job.jobType || '全职'} · {job.education || '不限'}
                      </span>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </motion.div>

        {/* Recent applications */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4, type: 'spring', stiffness: 200, damping: 30 }}
        >
          <Card
            bordered={false}
            className="!border-0 !shadow-sm h-full"
            title={<span className="font-medium text-gray-700">最新申请</span>}
            loading={loading}
          >
            <List
              dataSource={recentApps}
              renderItem={(app) => (
                <List.Item className="!border-b-0">
                  <List.Item.Meta
                    title={
                      <div className="flex items-center justify-between w-full pr-4">
                        <span className="font-medium text-gray-900">{app.candidateName || '-'}</span>
                        <Tag color={statusColorMap[app.status]} className="!text-xs">
                          {app.statusDesc}
                        </Tag>
                      </div>
                    }
                    description={
                      <div className="flex items-center justify-between pr-4">
                        <span className="text-gray-400">{app.jobTitle || '-'}</span>
                        {app.matchScore && (
                          <span className="text-cyan-600 font-medium text-sm">
                            {app.matchScore}% 匹配
                          </span>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </motion.div>
      </div>

      {/* Quick actions */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5, type: 'spring', stiffness: 200, damping: 30 }}
        className="mt-5"
      >
        <Card bordered={false} className="!border-0 !shadow-sm" title={<span className="font-medium text-gray-700">快捷操作</span>}>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {quickActions.map((item) => (
              <button
                key={item.label}
                onClick={() => navigate(item.path)}
                className="p-4 rounded-xl border border-gray-100 bg-gray-50 hover:bg-cyan-50 hover:border-cyan-200 transition-all text-left group"
              >
                <div className="text-cyan-600 text-xl mb-2 group-hover:scale-110 transition-transform">
                  {item.icon}
                </div>
                <div className="font-medium text-gray-700 text-sm">{item.label}</div>
                <div className="text-gray-400 text-xs">{item.desc}</div>
              </button>
            ))}
          </div>
        </Card>
      </motion.div>
    </PageTransition>
  );
}
