import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Typography,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
  Badge,
  Checkbox,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ApiOutlined,
  SendOutlined,
  LoadingOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { webhookApi } from '../../api';
import type { WebhookResponse, WebhookEventType } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;

const eventCategoryMap: Record<
  string,
  { label: string; color: string; events: WebhookEventType[] }
> = {
  resume: {
    label: '简历',
    color: 'blue',
    events: ['resume.uploaded', 'resume.parse_completed', 'resume.parse_failed'],
  },
  candidate: {
    label: '候选人',
    color: 'green',
    events: ['candidate.created', 'candidate.updated'],
  },
  application: {
    label: '申请',
    color: 'purple',
    events: ['application.submitted', 'application.status_changed'],
  },
  interview: {
    label: '面试',
    color: 'orange',
    events: ['interview.scheduled', 'interview.completed', 'interview.cancelled'],
  },
  system: {
    label: '系统',
    color: 'red',
    events: ['system.error', 'system.maintenance'],
  },
};

export default function SettingsPage() {
  const [webhooks, setWebhooks] = useState<WebhookResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModal, setCreateModal] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadWebhooks();
  }, []);

  const loadWebhooks = async () => {
    setLoading(true);
    try {
      const { data } = await webhookApi.list();
      setWebhooks(data.data || []);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    await webhookApi.create(values);
    message.success('Webhook 创建成功');
    setCreateModal(false);
    form.resetFields();
    loadWebhooks();
  };

  const handleDelete = async (id: number) => {
    await webhookApi.delete(id);
    message.success('已删除');
    loadWebhooks();
  };

  const handleTest = async (id: number) => {
    setTestingId(id);
    try {
      await webhookApi.test(id);
      message.success('测试消息已发送');
    } catch {
      message.error('测试发送失败');
    } finally {
      setTestingId(null);
    }
  };

  const columns = [
    {
      title: 'URL',
      dataIndex: 'url',
      width: 280,
      render: (url: string) => (
        <span className="flex items-center gap-2">
          <LinkOutlined className="text-cyan-600" />
          <Text copyable ellipsis style={{ maxWidth: 240 }}>
            {url}
          </Text>
        </span>
      ),
    },
    {
      title: '事件类型',
      dataIndex: 'events',
      width: 240,
      render: (events: string[]) => (
        <Space size={4} wrap>
          {events?.slice(0, 3).map((e) => {
            const cat = Object.values(eventCategoryMap).find((c) =>
              c.events.includes(e as WebhookEventType)
            );
            return <Tag key={e} color={cat?.color || 'default'}>
              {e}
            </Tag>;
          })}
          {events?.length > 3 && <Tag>+{events.length - 3}</Tag>}
        </Space>
      ),
    },
    {
      title: '签名密钥',
      dataIndex: 'secretHint',
      width: 100,
      render: (v: string) =>
        v ? (
          <Text code>{v}</Text>
        ) : (
          <span className="text-gray-400">未设置</span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (active: boolean) =>
        active !== false ? (
          <Badge status="success" text="活跃" />
        ) : (
          <Badge status="default" text="禁用" />
        ),
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, record: WebhookResponse) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={testingId === record.id ? <LoadingOutlined /> : <SendOutlined />}
            loading={testingId === record.id}
            onClick={() => handleTest(record.id)}
          >
            测试
          </Button>
          <Popconfirm title="确定删除此 Webhook？" onConfirm={() => handleDelete(record.id)}>
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
      <h2 className="text-xl font-semibold text-gray-900 mb-6">系统设置</h2>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* Webhook 管理 */}
        <motion.div variants={staggerItem}>
          <Card
            bordered={false}
            title={
              <span className="flex items-center gap-2">
                <ApiOutlined className="text-cyan-600" />
                <span>Webhook 配置</span>
              </span>
            }
            extra={
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setCreateModal(true)}
              >
                新建 Webhook
              </Button>
            }
          >
            <Table
              rowKey="id"
              columns={columns}
              dataSource={webhooks}
              loading={loading}
              scroll={{ x: true }}
              pagination={false}
            />
          </Card>
        </motion.div>
      </motion.div>

      {/* 创建 Webhook 弹窗 */}
      <Modal
        title="新建 Webhook"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        okText="创建"
        width={580}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            name="url"
            label="Webhook URL"
            rules={[
              { required: true, message: '请输入 URL' },
              { type: 'url', message: '请输入有效的 URL' },
            ]}
          >
            <Input
              prefix={<LinkOutlined />}
              placeholder="https://example.com/webhook"
            />
          </Form.Item>

          <Form.Item name="secret" label="签名密钥（HMAC-SHA256）">
            <Input.Password placeholder="可选，用于验证消息签名" />
          </Form.Item>

          <Form.Item
            name="events"
            label="订阅事件"
            rules={[{ required: true, message: '至少选择一个事件' }]}
          >
            <Checkbox.Group className="w-full">
              {Object.entries(eventCategoryMap).map(([catKey, catData]) => (
                <div key={catKey} className="mb-4">
                  <span className="font-semibold">
                    <Tag color={catData.color} className="mr-2">
                      {catData.label}
                    </Tag>
                  </span>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-2 mt-2">
                    {catData.events.map((event) => (
                      <Checkbox key={event} value={event} className="mb-1">
                        <span className="text-[13px]">{event}</span>
                      </Checkbox>
                    ))}
                  </div>
                </div>
              ))}
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </PageTransition>
  );
}
