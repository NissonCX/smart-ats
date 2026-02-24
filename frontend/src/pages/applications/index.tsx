import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Select,
  Typography,
  Button,
  Modal,
  Form,
  InputNumber,
  message,
  Statistic,
  Drawer,
  Progress,
  Descriptions,
  Tooltip,
  Spin,
} from 'antd';
import {
  PlusOutlined,
  ArrowRightOutlined,
  UserOutlined,
  FolderOutlined,
  ThunderboltOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { applicationApi } from '../../api';
import type { ApplicationResponse, ApplicationStatus, ApplicationQueryParams, MatchScoreResponse } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;

const statusFlow: Record<ApplicationStatus, ApplicationStatus[]> = {
  PENDING: ['SCREENING', 'REJECTED'],
  SCREENING: ['INTERVIEW', 'REJECTED'],
  INTERVIEW: ['OFFER', 'REJECTED'],
  OFFER: [],
  REJECTED: [],
  WITHDRAWN: [],
};

const statusConfig: Record<ApplicationStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待处理' },
  SCREENING: { color: 'processing', label: '筛选中' },
  INTERVIEW: { color: 'warning', label: '面试中' },
  OFFER: { color: 'success', label: '已录用' },
  REJECTED: { color: 'error', label: '已拒绝' },
  WITHDRAWN: { color: 'default', label: '已撤回' },
};

export default function ApplicationsPage() {
  const [applications, setApplications] = useState<ApplicationResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<ApplicationQueryParams>({ pageNum: 1, pageSize: 10 });
  const [createModal, setCreateModal] = useState(false);
  const [form] = Form.useForm();
  const [scoreDrawer, setScoreDrawer] = useState(false);
  const [scoreDetail, setScoreDetail] = useState<MatchScoreResponse | null>(null);
  const [scoreLoading, setScoreLoading] = useState(false);
  const [calculatingId, setCalculatingId] = useState<number | null>(null);

  const statusCounts = applications.reduce(
    (acc, app) => {
      acc[app.status] = (acc[app.status] || 0) + 1;
      return acc;
    },
    {} as Record<string, number>
  );

  const loadApplications = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await applicationApi.list(query);
      setApplications(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  const handleCreate = async () => {
    const values = await form.validateFields();
    await applicationApi.create(values);
    message.success('创建成功');
    setCreateModal(false);
    form.resetFields();
    loadApplications();
  };

  const handleStatusChange = async (id: number, newStatus: ApplicationStatus) => {
    await applicationApi.updateStatus(id, { status: newStatus });
    message.success('状态已更新');
    loadApplications();
  };

  const handleCalculateScore = async (id: number) => {
    setCalculatingId(id);
    try {
      const { data } = await applicationApi.calculateMatchScore(id);
      if (data.data) {
        setScoreDetail(data.data);
        setScoreDrawer(true);
        message.success('匹配分数计算完成');
        loadApplications();
      }
    } catch {
      message.error('匹配分数计算失败');
    } finally {
      setCalculatingId(null);
    }
  };

  const handleViewScore = async (record: ApplicationResponse) => {
    if (record.matchScore !== undefined && record.matchScore !== null) {
      // Try to fetch fresh score detail
      setScoreDrawer(true);
      setScoreLoading(true);
      try {
        const { data } = await applicationApi.calculateMatchScore(record.id);
        if (data.data) {
          setScoreDetail(data.data);
        }
      } catch {
        // Fallback: show basic score info from list data
        setScoreDetail({
          applicationId: record.id,
          jobId: record.jobId,
          candidateId: record.candidateId,
          totalScore: record.matchScore,
          breakdown: { semanticScore: 0, skillScore: 0, experienceScore: 0, educationScore: 0 },
          matchReasons: record.matchReasons || [],
          calculatedAt: record.matchCalculatedAt || '',
        });
      } finally {
        setScoreLoading(false);
      }
    } else {
      handleCalculateScore(record.id);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: '职位',
      dataIndex: 'jobTitle',
      width: 160,
      render: (v: string) => (
        <Space>
          <FolderOutlined className="text-cyan-600" />
          <Text ellipsis style={{ maxWidth: 120 }}>
            {v || '-'}
          </Text>
        </Space>
      ),
    },
    {
      title: '候选人',
      dataIndex: 'candidateName',
      width: 120,
      render: (v: string) => (
        <Space>
          <UserOutlined />
          {v || '-'}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: ApplicationStatus) => (
        <Tag color={statusConfig[status]?.color}>{statusConfig[status]?.label}</Tag>
      ),
    },
    {
      title: '匹配分数',
      dataIndex: 'matchScore',
      width: 140,
      render: (score: number, record: ApplicationResponse) => {
        if (score === undefined || score === null) {
          return (
            <Tooltip title="计算 AI 匹配分数">
              <Button
                type="link"
                size="small"
                icon={<ThunderboltOutlined />}
                loading={calculatingId === record.id}
                onClick={() => handleCalculateScore(record.id)}
              >
                计算
              </Button>
            </Tooltip>
          );
        }
        const color =
          score >= 80
            ? '#10b981'
            : score >= 60
              ? '#f59e0b'
              : '#ef4444';
        return (
          <Tooltip title="点击查看匹配详情">
            <span
              className="cursor-pointer hover:opacity-70 transition-opacity"
              onClick={() => handleViewScore(record)}
            >
              <Progress
                type="circle"
                percent={score}
                size={36}
                strokeColor={color}
                format={(p) => <span className="text-xs font-bold">{p}</span>}
              />
            </span>
          </Tooltip>
        );
      },
    },
    { title: '申请时间', dataIndex: 'createdAt', width: 160 },
    {
      title: '操作',
      width: 160,
      render: (_: unknown, record: ApplicationResponse) => {
        const nextStatuses = statusFlow[record.status] || [];
        if (nextStatuses.length === 0)
          return <Text type="secondary">-</Text>;
        return (
          <Space size={4} wrap>
            {nextStatuses.map((s) => (
              <Button
                key={s}
                type="link"
                size="small"
                danger={s === 'REJECTED'}
                onClick={() => handleStatusChange(record.id, s)}
              >
                {statusConfig[s]?.label} <ArrowRightOutlined />
              </Button>
            ))}
          </Space>
        );
      },
    },
  ];

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">职位申请</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateModal(true)}
        >
          新建申请
        </Button>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* 统计卡片 */}
        <motion.div variants={staggerItem}>
          <div className="grid grid-cols-3 sm:grid-cols-6 gap-3 sm:gap-4 mb-4">
            {Object.entries(statusConfig).map(([key, cfg]) => (
              <Card bordered={false} key={key} className="text-center">
                <Statistic
                  title={<Tag color={cfg.color}>{cfg.label}</Tag>}
                  value={statusCounts[key] || 0}
                  valueStyle={{ fontSize: 20, fontWeight: 700 }}
                />
              </Card>
            ))}
          </div>
        </motion.div>

        {/* 筛选 */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
              <div className="md:col-span-3">
                <Select
                  placeholder="状态"
                  allowClear
                  className="w-full"
                  onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
                  options={Object.entries(statusConfig).map(([k, v]) => ({
                    value: k,
                    label: v.label,
                  }))}
                />
              </div>
              <div className="md:col-span-3">
                <InputNumber
                  placeholder="职位 ID"
                  min={1}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, jobId: v ?? undefined, pageNum: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-3">
                <InputNumber
                  placeholder="候选人 ID"
                  min={1}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, candidateId: v ?? undefined, pageNum: 1 }))
                  }
                />
              </div>
            </div>
          </Card>
        </motion.div>

        {/* 表格 */}
        <motion.div variants={staggerItem}>
          <Card bordered={false}>
            <Table
              rowKey="id"
              columns={columns}
              dataSource={applications}
              loading={loading}
              scroll={{ x: true }}
              pagination={{
                current: query.pageNum,
                pageSize: query.pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (p, s) =>
                  setQuery((q) => ({ ...q, pageNum: p, pageSize: s })),
              }}
            />
          </Card>
        </motion.div>
      </motion.div>

      {/* 新建申请弹窗 */}
      <Modal
        title="新建职位申请"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        okText="创建"
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            name="jobId"
            label="职位 ID"
            rules={[{ required: true, message: '请输入职位 ID' }]}
          >
            <InputNumber min={1} className="w-full" placeholder="输入职位 ID" />
          </Form.Item>
          <Form.Item
            name="candidateId"
            label="候选人 ID"
            rules={[{ required: true, message: '请输入候选人 ID' }]}
          >
            <InputNumber min={1} className="w-full" placeholder="输入候选人 ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 匹配分数详情抽屉 */}
      <Drawer
        title="AI 匹配分数详情"
        open={scoreDrawer}
        onClose={() => { setScoreDrawer(false); setScoreDetail(null); }}
        width={520}
        extra={
          scoreDetail && (
            <Button
              icon={<ReloadOutlined />}
              loading={calculatingId === scoreDetail.applicationId}
              onClick={() => handleCalculateScore(scoreDetail.applicationId)}
            >
              重新计算
            </Button>
          )
        }
      >
        <Spin spinning={scoreLoading}>
          {scoreDetail && (
            <div className="space-y-6">
              {/* 综合得分 */}
              <div className="text-center py-4">
                <Progress
                  type="dashboard"
                  percent={scoreDetail.totalScore}
                  size={160}
                  strokeColor={{
                    '0%': scoreDetail.totalScore >= 70 ? '#10b981' : scoreDetail.totalScore >= 50 ? '#f59e0b' : '#ef4444',
                    '100%': scoreDetail.totalScore >= 70 ? '#059669' : scoreDetail.totalScore >= 50 ? '#d97706' : '#dc2626',
                  }}
                  format={(p) => (
                    <div>
                      <div className="text-3xl font-bold">{p}</div>
                      <div className="text-xs text-gray-500">综合匹配分</div>
                    </div>
                  )}
                />
              </div>

              {/* 维度分解 */}
              <Card title="多维度评分" bordered={false} className="!shadow-none !bg-gray-50">
                <div className="space-y-4">
                  {[
                    { label: '技能匹配', value: scoreDetail.breakdown.skillScore, weight: '35%', color: '#3b82f6' },
                    { label: '语义相似度', value: scoreDetail.breakdown.semanticScore, weight: '30%', color: '#8b5cf6' },
                    { label: '经验匹配', value: scoreDetail.breakdown.experienceScore, weight: '20%', color: '#f59e0b' },
                    { label: '学历匹配', value: scoreDetail.breakdown.educationScore, weight: '15%', color: '#10b981' },
                  ].map((dim) => (
                    <div key={dim.label}>
                      <div className="flex justify-between mb-1">
                        <span className="text-sm text-gray-700">
                          {dim.label}
                          <span className="text-xs text-gray-400 ml-1">({dim.weight})</span>
                        </span>
                        <span className="text-sm font-semibold">{Number(dim.value).toFixed(1)}</span>
                      </div>
                      <Progress
                        percent={Number(dim.value)}
                        showInfo={false}
                        strokeColor={dim.color}
                        size="small"
                      />
                    </div>
                  ))}
                </div>
              </Card>

              {/* 匹配理由 */}
              {scoreDetail.matchReasons?.length > 0 && (
                <Card title="匹配理由" bordered={false} className="!shadow-none !bg-gray-50">
                  <ul className="list-disc pl-4 space-y-1 m-0">
                    {scoreDetail.matchReasons.map((reason, i) => (
                      <li key={i} className="text-sm text-gray-700">{reason}</li>
                    ))}
                  </ul>
                </Card>
              )}

              {/* 基本信息 */}
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label="申请 ID">{scoreDetail.applicationId}</Descriptions.Item>
                <Descriptions.Item label="职位 ID">{scoreDetail.jobId}</Descriptions.Item>
                <Descriptions.Item label="候选人 ID">{scoreDetail.candidateId}</Descriptions.Item>
                <Descriptions.Item label="计算时间">{scoreDetail.calculatedAt || '-'}</Descriptions.Item>
              </Descriptions>
            </div>
          )}
        </Spin>
      </Drawer>
    </PageTransition>
  );
}
