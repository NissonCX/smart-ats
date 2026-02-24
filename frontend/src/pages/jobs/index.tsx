import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Button,
  Card,
  Space,
  Tag,
  Input,
  Select,
  Modal,
  Form,
  InputNumber,
  message,
  Popconfirm,
  Row,
  Col,
  Typography,
  Drawer,
  Descriptions,
  Divider,
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { jobApi } from '../../api';
import type { JobResponse, JobStatus, JobQueryParams } from '../../types';

const { Title, Paragraph, Text } = Typography;

const statusConfig: Record<JobStatus, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'success', label: '已发布' },
  CLOSED: { color: 'error', label: '已关闭' },
};

export default function JobsPage() {
  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<JobQueryParams>({ pageNum: 1, pageSize: 10 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingJob, setEditingJob] = useState<JobResponse | null>(null);
  const [detailDrawer, setDetailDrawer] = useState<JobResponse | null>(null);
  const [form] = Form.useForm();

  const loadJobs = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await jobApi.list(query);
      setJobs(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  const handleCreate = () => {
    setEditingJob(null);
    form.resetFields();
    setModalOpen(true);
  };

  const handleEdit = (job: JobResponse) => {
    setEditingJob(job);
    form.setFieldsValue({
      ...job,
      requiredSkills: job.requiredSkills?.join(', '),
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const skills = values.requiredSkills
      ? values.requiredSkills.split(/[,，]/).map((s: string) => s.trim()).filter(Boolean)
      : undefined;

    const payload = { ...values, requiredSkills: skills };

    if (editingJob) {
      await jobApi.update({ ...payload, id: editingJob.id });
      message.success('更新成功');
    } else {
      await jobApi.create(payload);
      message.success('创建成功');
    }
    setModalOpen(false);
    loadJobs();
  };

  const handlePublish = async (id: number) => {
    await jobApi.publish(id);
    message.success('已发布');
    loadJobs();
  };

  const handleClose = async (id: number) => {
    await jobApi.close(id);
    message.success('已关闭');
    loadJobs();
  };

  const handleDelete = async (id: number) => {
    await jobApi.delete(id);
    message.success('已删除');
    loadJobs();
  };

  const columns = [
    {
      title: '职位名称',
      dataIndex: 'title',
      width: 200,
      render: (text: string, record: JobResponse) => (
        <a onClick={() => setDetailDrawer(record)}>{text}</a>
      ),
    },
    {
      title: '部门',
      dataIndex: 'department',
      width: 120,
      render: (v: string) => v || '-',
    },
    {
      title: '薪资范围',
      dataIndex: 'salaryRange',
      width: 120,
      render: (v: string) => <Text strong style={{ color: '#f59e0b' }}>{v}</Text>,
    },
    {
      title: '经验',
      dataIndex: 'experienceRange',
      width: 100,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: JobStatus) => (
        <Tag color={statusConfig[status]?.color}>{statusConfig[status]?.label}</Tag>
      ),
    },
    {
      title: '浏览量',
      dataIndex: 'viewCount',
      width: 80,
      sorter: true,
      render: (v: number) => (
        <Space size={4}>
          <EyeOutlined style={{ color: '#94a3b8' }} />
          {v}
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
    },
    {
      title: '操作',
      width: 240,
      render: (_: unknown, record: JobResponse) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          {record.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<SendOutlined />} onClick={() => handlePublish(record.id)}>
              发布
            </Button>
          )}
          {record.status === 'PUBLISHED' && (
            <Button type="link" size="small" icon={<StopOutlined />} danger onClick={() => handleClose(record.id)}>
              关闭
            </Button>
          )}
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
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>职位管理</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
          新建职位
        </Button>
      </div>

      {/* 搜索栏 */}
      <Card bordered={false} bodyStyle={{ paddingBottom: 0 }} style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Input
              placeholder="搜索职位名称"
              prefix={<SearchOutlined />}
              allowClear
              onChange={(e) => setQuery((q) => ({ ...q, keyword: e.target.value, pageNum: 1 }))}
            />
          </Col>
          <Col span={4}>
            <Select
              placeholder="状态"
              allowClear
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
              options={[
                { value: 'DRAFT', label: '草稿' },
                { value: 'PUBLISHED', label: '已发布' },
                { value: 'CLOSED', label: '已关闭' },
              ]}
            />
          </Col>
          <Col span={4}>
            <Select
              placeholder="职位类型"
              allowClear
              style={{ width: '100%' }}
              onChange={(v) => setQuery((q) => ({ ...q, jobType: v, pageNum: 1 }))}
              options={[
                { value: '全职', label: '全职' },
                { value: '兼职', label: '兼职' },
                { value: '实习', label: '实习' },
              ]}
            />
          </Col>
        </Row>
      </Card>

      {/* 表格 */}
      <Card bordered={false}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={jobs}
          loading={loading}
          pagination={{
            current: query.pageNum,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setQuery((q) => ({ ...q, pageNum: page, pageSize: size })),
          }}
        />
      </Card>

      {/* 新建 / 编辑弹窗 */}
      <Modal
        title={editingJob ? '编辑职位' : '新建职位'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        width={640}
        okText={editingJob ? '更新' : '创建'}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="职位名称" rules={[{ required: true }]}>
            <Input placeholder="如：高级 Java 工程师" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="department" label="部门">
                <Input placeholder="如：技术部" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="jobType" label="职位类型">
                <Select
                  placeholder="选择类型"
                  options={[
                    { value: '全职', label: '全职' },
                    { value: '兼职', label: '兼职' },
                    { value: '实习', label: '实习' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="salaryMin" label="最低薪资(K)" rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="salaryMax" label="最高薪资(K)" rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="experienceMin" label="最低经验(年)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="experienceMax" label="最高经验(年)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="education" label="学历要求">
                <Select
                  placeholder="选择学历"
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
          <Form.Item name="requiredSkills" label="技能标签（逗号分隔）">
            <Input placeholder="如：Java, Spring Boot, MySQL" />
          </Form.Item>
          <Form.Item name="description" label="职位描述" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="职位描述..." />
          </Form.Item>
          <Form.Item name="requirements" label="任职要求" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="任职要求..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情抽屉 */}
      <Drawer
        title={detailDrawer?.title}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={560}
      >
        {detailDrawer && (
          <>
            <Descriptions column={2}>
              <Descriptions.Item label="部门">{detailDrawer.department || '-'}</Descriptions.Item>
              <Descriptions.Item label="类型">{detailDrawer.jobType || '全职'}</Descriptions.Item>
              <Descriptions.Item label="薪资">
                <Text strong style={{ color: '#f59e0b' }}>{detailDrawer.salaryRange}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="经验">{detailDrawer.experienceRange}</Descriptions.Item>
              <Descriptions.Item label="学历">{detailDrawer.education || '不限'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusConfig[detailDrawer.status]?.color}>
                  {statusConfig[detailDrawer.status]?.label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="浏览量">{detailDrawer.viewCount}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{detailDrawer.createdAt}</Descriptions.Item>
            </Descriptions>
            <Divider />
            {detailDrawer.requiredSkills?.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <Text strong>技能标签</Text>
                <div style={{ marginTop: 8 }}>
                  {detailDrawer.requiredSkills.map((s) => (
                    <Tag color="blue" key={s}>{s}</Tag>
                  ))}
                </div>
              </div>
            )}
            <Title level={5}>职位描述</Title>
            <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{detailDrawer.description}</Paragraph>
            <Title level={5}>任职要求</Title>
            <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{detailDrawer.requirements}</Paragraph>
          </>
        )}
      </Drawer>
    </div>
  );
}
