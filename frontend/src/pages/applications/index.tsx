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
} from 'antd';
import {
  PlusOutlined,
  ArrowRightOutlined,
  UserOutlined,
  FolderOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { applicationApi } from '../../api';
import type { ApplicationResponse, ApplicationStatus, ApplicationQueryParams } from '../../types';
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
      width: 100,
      render: (score: number) => {
        if (score === undefined || score === null) return '-';
        const color =
          score >= 80
            ? 'text-emerald-600'
            : score >= 60
              ? 'text-amber-600'
              : 'text-red-500';
        return <span className={`font-semibold ${color}`}>{score} 分</span>;
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
    </PageTransition>
  );
}
