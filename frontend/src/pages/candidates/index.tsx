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
import { motion } from 'framer-motion';
import { candidateApi } from '../../api';
import type { CandidateResponse, CandidateQueryParams, WorkExperienceItem } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text, Paragraph } = Typography;

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
    form.setFieldsValue({ ...record, skills: record.skills?.join(', ') });
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
        <a
          className="text-gray-900 hover:text-cyan-600 transition-colors font-medium cursor-pointer"
          onClick={() => setDetailDrawer(record)}
        >
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
      width: 160,
      render: (v: string) => <span className="text-gray-500">{v || '-'}</span>,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      width: 140,
      render: (v: string) => <span className="text-gray-500">{v || '-'}</span>,
    },
    { title: '学历', dataIndex: 'education', width: 90, render: (v: string) => v || '-' },
    {
      title: '工作年限',
      dataIndex: 'workYears',
      width: 90,
      render: (v: number) => (v !== undefined && v !== null ? `${v} 年` : '-'),
    },
    {
      title: '技能',
      dataIndex: 'skills',
      width: 200,
      render: (skills: string[]) =>
        skills?.length ? (
          <div className="flex flex-wrap gap-1">
            {skills.slice(0, 3).map((s) => (
              <Tag key={s} color="blue">
                {s}
              </Tag>
            ))}
            {skills.length > 3 && <Tag>+{skills.length - 3}</Tag>}
          </div>
        ) : (
          '-'
        ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
    {
      title: '操作',
      width: 160,
      render: (_: unknown, record: CandidateResponse) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setDetailDrawer(record)}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
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
    <PageTransition>
      <h2 className="text-xl font-semibold text-gray-900 mb-6">候选人管理</h2>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* 搜索筛选 */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
              <div className="md:col-span-3">
                <Input
                  placeholder="搜索姓名"
                  prefix={<SearchOutlined className="text-gray-400" />}
                  allowClear
                  onChange={(e) => setQuery((q) => ({ ...q, keyword: e.target.value, page: 1 }))}
                />
              </div>
              <div className="md:col-span-2">
                <Select
                  placeholder="学历"
                  allowClear
                  className="w-full"
                  onChange={(v) => setQuery((q) => ({ ...q, education: v, page: 1 }))}
                  options={[
                    { value: '大专', label: '大专' },
                    { value: '本科', label: '本科' },
                    { value: '硕士', label: '硕士' },
                    { value: '博士', label: '博士' },
                  ]}
                />
              </div>
              <div className="md:col-span-3">
                <Input
                  placeholder="技能关键词"
                  allowClear
                  onChange={(e) =>
                    setQuery((q) => ({ ...q, skill: e.target.value, page: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-2">
                <InputNumber
                  placeholder="最少工作年限"
                  min={0}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, minWorkYears: v ?? undefined, page: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-2">
                <InputNumber
                  placeholder="最多工作年限"
                  min={0}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, maxWorkYears: v ?? undefined, page: 1 }))
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
              dataSource={candidates}
              loading={loading}
              scroll={{ x: true }}
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
        </motion.div>
      </motion.div>

      {/* 详情抽屉 */}
      <Drawer
        title={detailDrawer ? `${detailDrawer.name} - 候选人详情` : ''}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={560}
      >
        {detailDrawer && (
          <div className="space-y-6">
            {/* 头部信息卡 */}
            <div className="flex items-center gap-4 p-4 rounded-xl bg-gradient-to-br from-gray-50 to-cyan-50/50">
              <div className="w-14 h-14 rounded-full bg-gray-900 text-white flex items-center justify-center text-xl font-semibold shrink-0">
                {detailDrawer.name?.charAt(0)}
              </div>
              <div>
                <h3 className="text-lg font-semibold text-gray-900 m-0">
                  {detailDrawer.name}
                </h3>
                <div className="flex flex-wrap gap-4 mt-1 text-gray-500 text-sm">
                  {detailDrawer.phone && (
                    <span>
                      <PhoneOutlined className="mr-1" />
                      {detailDrawer.phone}
                    </span>
                  )}
                  {detailDrawer.email && (
                    <span>
                      <MailOutlined className="mr-1" />
                      {detailDrawer.email}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <Descriptions column={2} size="small">
              <Descriptions.Item label="学历">
                {detailDrawer.education || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="工作年限">
                {detailDrawer.workYears ?? '-'} 年
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {detailDrawer.createdAt}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {detailDrawer.updatedAt}
              </Descriptions.Item>
            </Descriptions>

            {detailDrawer.skills?.length > 0 && (
              <>
                <Divider className="my-3" />
                <div>
                  <p className="font-semibold text-gray-900 mb-2">技能标签</p>
                  <div className="flex flex-wrap gap-1.5">
                    {detailDrawer.skills.map((s) => (
                      <Tag color="blue" key={s}>
                        {s}
                      </Tag>
                    ))}
                  </div>
                </div>
              </>
            )}

            {detailDrawer.workExperience?.length > 0 && (
              <>
                <Divider className="my-3" />
                <div>
                  <p className="font-semibold text-gray-900 mb-2">工作经历</p>
                  {detailDrawer.workExperience.map(
                    (exp: WorkExperienceItem, idx: number) => (
                      <div
                        key={idx}
                        className="mt-3 p-3 bg-gray-50 rounded-lg"
                      >
                        <Text strong>{exp.company}</Text>
                        <Text type="secondary" className="ml-2">
                          {exp.position}
                        </Text>
                        <br />
                        <Text type="secondary" className="text-xs">
                          {exp.startDate} - {exp.endDate || '至今'}
                        </Text>
                        {exp.description && (
                          <Paragraph className="mt-1 mb-0 text-sm text-gray-600">
                            {exp.description}
                          </Paragraph>
                        )}
                      </div>
                    )
                  )}
                </div>
              </>
            )}

            {detailDrawer.aiSummary && (
              <>
                <Divider className="my-3" />
                <div>
                  <p className="font-semibold text-gray-900 mb-2">AI 摘要</p>
                  <Paragraph className="text-gray-600">
                    {detailDrawer.aiSummary}
                  </Paragraph>
                </div>
              </>
            )}
          </div>
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
        <Form form={form} layout="vertical" className="mt-4">
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="name" label="姓名" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="电话">
              <Input />
            </Form.Item>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="email" label="邮箱">
              <Input />
            </Form.Item>
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
          </div>
          <Form.Item name="workYears" label="工作年限">
            <InputNumber min={0} className="w-full" />
          </Form.Item>
          <Form.Item name="skills" label="技能标签（逗号分隔）">
            <Input placeholder="如：Java, Python, React" />
          </Form.Item>
          <Form.Item name="summary" label="个人摘要">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </PageTransition>
  );
}
