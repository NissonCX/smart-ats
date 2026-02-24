import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Input,
  Select,
  Typography,
  Button,
  Drawer,
  Descriptions,
  Form,
  Modal,
  message,
  Popconfirm,
  Row,
  Col,
  Divider,
  InputNumber,
} from 'antd';
import {
  SearchOutlined,
  UserOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  PhoneOutlined,
  MailOutlined,
} from '@ant-design/icons';
import { candidateApi } from '../../api';
import type { CandidateResponse, CandidateQueryParams, WorkExperienceItem } from '../../types';

const { Title, Text, Paragraph } = Typography;

export default function CandidatesPage() {
  const [candidates, setCandidates] = useState<CandidateResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<CandidateQueryParams>({ page: 1, pageSize: 10 });
  const [detailDrawer, setDetailDrawer] = useState<CandidateResponse | null>(null);
  const [editModal, setEditModal] = useState<CandidateResponse | null>(null);
  const [form] = Form.useForm();

  const loadCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await candidateApi.list(query);
      setCandidates(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadCandidates();
  }, [loadCandidates]);

  const handleEdit = (record: CandidateResponse) => {
    setEditModal(record);
    form.setFieldsValue({
      ...record,
      skills: record.skills?.join(', '),
    });
  };

  const handleUpdate = async () => {
    const values = await form.validateFields();
    const skills = values.skills
      ? values.skills.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean)
      : undefined;
    await candidateApi.update(editModal!.id, { ...values, skills });
    message.success('更新成功');
    setEditModal(null);
    loadCandidates();
  };

  const handleDelete = async (id: number) => {
    await candidateApi.delete(id);
    message.success('已删除');
    loadCandidates();
  };

  const columns = [
    {
      title: '姓名',
      dataIndex: 'name',
      width: 120,
      render: (name: string, record: CandidateResponse) => (
        <a onClick={() => setDetailDrawer(record)}>
          <Space>
            <UserOutlined />
            {name}
          </Space>
        </a>
      ),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 180,
      render: (v: string) => v || '-',
    },
    {
      title: '电话',
      dataIndex: 'phone',
      width: 140,
      render: (v: string) => v || '-',
    },
    {
      title: '学历',
      dataIndex: 'education',
      width: 100,
      render: (v: string) => v || '-',
    },
    {
      title: '工作年限',
      dataIndex: 'workYears',
      width: 90,
      render: (v: number) => (v !== undefined && v !== null ? `${v} 年` : '-'),
    },
    {
      title: '技能',
      dataIndex: 'skills',
      width: 240,
      render: (skills: string[]) =>
        skills?.length ? (
          <Space size={4} wrap>
            {skills.slice(0, 4).map((s) => (
              <Tag key={s} color="blue">{s}</Tag>
            ))}
            {skills.length > 4 && <Tag>+{skills.length - 4}</Tag>}
          </Space>
        ) : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
    },
    {
      title: '操作',
      width: 160,
      render: (_: unknown, record: CandidateResponse) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setDetailDrawer(record)}>
            查看
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" icon={<DeleteOutlined />} danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>候选人管理</Title>

      {/* 搜索筛选 */}
      <Card bordered={false} bodyStyle={{ paddingBottom: 0 }} style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={5}>
            <Input
              placeholder="搜索姓名"
              prefix={<SearchOutlined />}
              allowClear
              onChange={(e) => setQuery((q) => ({ ...q, keyword: e.target.value, page: 1 }))}
            />
          </Col>
          <Col span={4}>
            <Select
              placeholder="学历"
              allowClear
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, education: v, page: 1 }))}
              options={[
                { value: '大专', label: '大专' },
                { value: '本科', label: '本科' },
                { value: '硕士', label: '硕士' },
                { value: '博士', label: '博士' },
              ]}
            />
          </Col>
          <Col span={4}>
            <Input
              placeholder="技能关键词"
              allowClear
              onChange={(e) => setQuery((q) => ({ ...q, skill: e.target.value, page: 1 }))}
            />
          </Col>
          <Col span={3}>
            <InputNumber
              placeholder="最少工作年限"
              min={0}
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, minWorkYears: v ?? undefined, page: 1 }))}
            />
          </Col>
          <Col span={3}>
            <InputNumber
              placeholder="最多工作年限"
              min={0}
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, maxWorkYears: v ?? undefined, page: 1 }))}
            />
          </Col>
        </Row>
      </Card>

      {/* 表格 */}
      <Card bordered={false}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={candidates}
          loading={loading}
          pagination={{
            current: query.page,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => setQuery((q) => ({ ...q, page: p, pageSize: s })),
          }}
        />
      </Card>

      {/* 详情抽屉 */}
      <Drawer
        title={detailDrawer ? `${detailDrawer.name} - 候选人详情` : ''}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={560}
      >
        {detailDrawer && (
          <>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 16,
                marginBottom: 24,
                padding: 16,
                borderRadius: 12,
                background: 'linear-gradient(135deg, #ede9fe 0%, #dbeafe 100%)',
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: '50%',
                  background: '#4f46e5',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 22,
                  fontWeight: 600,
                }}
              >
                {detailDrawer.name?.charAt(0)}
              </div>
              <div>
                <Title level={4} style={{ margin: 0 }}>{detailDrawer.name}</Title>
                <Space size={16} style={{ marginTop: 4, color: '#64748b' }}>
                  {detailDrawer.phone && (
                    <span><PhoneOutlined /> {detailDrawer.phone}</span>
                  )}
                  {detailDrawer.email && (
                    <span><MailOutlined /> {detailDrawer.email}</span>
                  )}
                </Space>
              </div>
            </div>

            <Descriptions column={2} size="small">
              <Descriptions.Item label="学历">{detailDrawer.education || '-'}</Descriptions.Item>
              <Descriptions.Item label="工作年限">{detailDrawer.workYears ?? '-'} 年</Descriptions.Item>
              <Descriptions.Item label="创建时间">{detailDrawer.createdAt}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{detailDrawer.updatedAt}</Descriptions.Item>
            </Descriptions>

            {detailDrawer.skills?.length > 0 && (
              <>
                <Divider />
                <Text strong>技能标签</Text>
                <div style={{ marginTop: 8 }}>
                  {detailDrawer.skills.map((s) => (
                    <Tag color="blue" key={s} style={{ marginBottom: 4 }}>{s}</Tag>
                  ))}
                </div>
              </>
            )}

            {detailDrawer.workExperience?.length > 0 && (
              <>
                <Divider />
                <Text strong>工作经历</Text>
                {detailDrawer.workExperience.map((exp: WorkExperienceItem, idx: number) => (
                  <div key={idx} style={{ marginTop: 12, padding: 12, background: '#f8fafc', borderRadius: 8 }}>
                    <Text strong>{exp.company}</Text>
                    <Text type="secondary" style={{ marginLeft: 8 }}>{exp.position}</Text>
                    <br />
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {exp.startDate} - {exp.endDate || '至今'}
                    </Text>
                    {exp.description && (
                      <Paragraph style={{ marginTop: 4, marginBottom: 0, fontSize: 13 }}>
                        {exp.description}
                      </Paragraph>
                    )}
                  </div>
                ))}
              </>
            )}

            {detailDrawer.aiSummary && (
              <>
                <Divider />
                <Text strong>AI 摘要</Text>
                <Paragraph style={{ marginTop: 8 }}>{detailDrawer.aiSummary}</Paragraph>
              </>
            )}
          </>
        )}
      </Drawer>

      {/* 编辑弹窗 */}
      <Modal
        title="编辑候选人"
        open={!!editModal}
        onCancel={() => setEditModal(null)}
        onOk={handleUpdate}
        okText="保存"
        width={560}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="姓名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="电话">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="email" label="邮箱">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="education" label="学历">
                <Select
                  allowClear
                  options={[
                    { value: '大专', label: '大专' },
                    { value: '本科', label: '本科' },
                    { value: '硕士', label: '硕士' },
                    { value: '博士', label: '博士' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="workYears" label="工作年限">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="skills" label="技能标签（逗号分隔）">
            <Input placeholder="如：Java, Python, React" />
          </Form.Item>
          <Form.Item name="summary" label="个人摘要">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
