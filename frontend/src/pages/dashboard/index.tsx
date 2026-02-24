import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Typography, Table, Tag, Space, List } from 'antd';
import {
  FileTextOutlined,
  TeamOutlined,
  SolutionOutlined,
  RiseOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { jobApi, applicationApi, candidateApi } from '../../api';
import { useAuthStore } from '../../store/auth';
import type { JobResponse, ApplicationResponse } from '../../types';

const { Title, Text } = Typography;

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
  const [hotJobs, setHotJobs] = useState<JobResponse[]>([]);
  const [recentApps, setRecentApps] = useState<ApplicationResponse[]>([]);
  const [stats, setStats] = useState({ jobs: 0, candidates: 0, applications: 0, interviews: 0 });
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
        interviews: 0,
      });
      setHotJobs(hotRes.data.data || []);
      setRecentApps(appsRes.data.data?.records || []);
    } catch {
      // handled
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
      color: '#4f46e5',
      bg: '#eef2ff',
    },
    {
      title: '候选人',
      value: stats.candidates,
      icon: <TeamOutlined />,
      color: '#0891b2',
      bg: '#ecfeff',
    },
    {
      title: '申请数',
      value: stats.applications,
      icon: <SolutionOutlined />,
      color: '#ca8a04',
      bg: '#fefce8',
    },
    {
      title: 'AI 额度',
      value: `${userInfo?.todayAiUsed || 0}/${userInfo?.dailyAiQuota || 0}`,
      icon: <RiseOutlined />,
      color: '#16a34a',
      bg: '#f0fdf4',
    },
  ];

  return (
    <div>
      {/* 问候 */}
      <div style={{ marginBottom: 24 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          {greetingTime()}，{userInfo?.username} 👋
        </Title>
        <Text type="secondary">这是你的招聘工作台，实时掌控招聘进展</Text>
      </div>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {statCards.map((item) => (
          <Col xs={24} sm={12} lg={6} key={item.title}>
            <Card bordered={false} bodyStyle={{ padding: '20px 24px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 13 }}>{item.title}</Text>
                  <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4 }}>
                    {typeof item.value === 'number' ? (
                      <Statistic value={item.value} valueStyle={{ fontSize: 28, fontWeight: 700 }} />
                    ) : (
                      item.value
                    )}
                  </div>
                </div>
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 12,
                    background: item.bg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 22,
                    color: item.color,
                  }}
                >
                  {item.icon}
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        {/* 热门职位 */}
        <Col xs={24} lg={12}>
          <Card title="🔥 热门职位" bordered={false} loading={loading}>
            <List
              dataSource={hotJobs}
              renderItem={(job) => (
                <List.Item
                  extra={
                    <Space>
                      <EyeOutlined style={{ color: '#94a3b8' }} />
                      <Text type="secondary">{job.viewCount}</Text>
                    </Space>
                  }
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        <Text strong>{job.title}</Text>
                        <Tag color="blue">{job.salaryRange}</Tag>
                      </Space>
                    }
                    description={`${job.department || '-'} · ${job.jobType || '全职'} · ${job.education || '不限'}`}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>

        {/* 最新申请 */}
        <Col xs={24} lg={12}>
          <Card title="📋 最新申请" bordered={false} loading={loading}>
            <Table
              dataSource={recentApps}
              pagination={false}
              rowKey="id"
              size="small"
              columns={[
                {
                  title: '候选人',
                  dataIndex: 'candidateName',
                  width: 100,
                },
                {
                  title: '职位',
                  dataIndex: 'jobTitle',
                  ellipsis: true,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 90,
                  render: (status: string, record: ApplicationResponse) => (
                    <Tag color={statusColorMap[status]}>{record.statusDesc}</Tag>
                  ),
                },
                {
                  title: '匹配度',
                  dataIndex: 'matchScore',
                  width: 80,
                  render: (v: number) => v ? `${v}%` : '-',
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
