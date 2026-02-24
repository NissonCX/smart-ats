import { useEffect, useState } from 'react';
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
  Row,
  Col,
  Statistic,
} from 'antd';
import {
  PlusOutlined,
  ArrowRightOutlined,
  UserOutlined,
  FolderOutlined,
} from '@ant-design/icons';
import { applicationApi } from '../../api';
import type { ApplicationResponse, ApplicationStatus, ApplicationQueryParams } from '../../types';

const { Title, Text } = Typography;

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

  // 统计
  const statusCounts = applications.reduce(
    (acc, app) => {
      acc[app.status] = (acc[app.status] || 0) + 1;
      return acc;
    },
    {} as Record<string, number>
  );

  useEffect(() => {
    loadApplications();
  }, [query]);

  const loadApplications = async () => {
    setLoading(true);
    try {
      const { data } = await applicationApi.list(query);
      setApplications(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  };

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
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '职位',
      dataIndex: 'jobTitle',
      width: 180,
      render: (v: string) => (
        <Space>
          <FolderOutlined style={{ color: '#4f46e5' }} />
          <Text ellipsis style={{ maxWidth: 140 }}>{v || '-'}</Text>
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
        const color = score >= 80 ? '#10b981' : score >= 60 ? '#f59e0b' : '#ef4444';
        return <Text style={{ color, fontWeight: 600 }}>{score} 分</Text>;
      },
    },
    {
      title: '申请时间',
      dataIndex: 'createdAt',
      width: 160,
    },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, record: ApplicationResponse) => {
        const nextStatuses = statusFlow[record.status] || [];
        if (nextStatuses.length === 0) return <Text type="secondary">-</Text>;
        return (
          <Space size={4}>
            {nextStatuses.map((s) => (
              <Button
                key={s}
                type="link"
                size="small"
                danger={s === 'REJECTED'}
                onClick={() => handleStatusChange(record.id, s)}
              >
                {statusConfig[s]?.label}
                <ArrowRightOutlined />
              </Button>
            ))}
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>职位申请</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModal(true)}>
          新建申请
        </Button>
      </div>

      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        {Object.entries(statusConfig).map(([key, cfg]) => (
          <Col span={4} key={key}>
            <Card bordered={false} bodyStyle={{ padding: '12px 16px' }}>
              <Statistic
                title={<Tag color={cfg.color}>{cfg.label}</Tag>}
                value={statusCounts[key] || 0}
                valueStyle={{ fontSize: 24, fontWeight: 700 }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      {/* 筛选 */}
      <Card bordered={false} bodyStyle={{ paddingBottom: 0 }} style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={4}>
            <Select
              placeholder="状态"
              allowClear
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
              options={Object.entries(statusConfig).map(([k, v]) => ({ value: k, label: v.label }))}
            />
          </Col>
          <Col span={4}>
            <InputNumber
              placeholder="职位 ID"
              min={1}
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, jobId: v ?? undefined, pageNum: 1 }))}
            />
          </Col>
          <Col span={4}>
            <InputNumber
              placeholder="候选人 ID"
              min={1}
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, candidateId: v ?? undefined, pageNum: 1 }))}
            />
          </Col>
        </Row>
      </Card>

      {/* 表格 */}
      <Card bordered={false}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={applications}
          loading={loading}
          pagination={{
            current: query.pageNum,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => setQuery((q) => ({ ...q, pageNum: p, pageSize: s })),
          }}
        />
      </Card>

      {/* 新建申请弹窗 */}
      <Modal
        title="新建职位申请"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        okText="创建"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="jobId" label="职位 ID" rules={[{ required: true, message: '请输入职位 ID' }]}>
            <InputNumber min={1} style={{ width: '100%' }} placeholder="输入职位 ID" />
          </Form.Item>
          <Form.Item name="candidateId" label="候选人 ID" rules={[{ required: true, message: '请输入候选人 ID' }]}>
            <InputNumber min={1} style={{ width: '100%' }} placeholder="输入候选人 ID" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
