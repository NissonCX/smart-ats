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
  Row,
  Col,
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
import { webhookApi } from '../../api';
import type { WebhookResponse, WebhookEventType } from '../../types';

const { Title, Text } = Typography;

const eventCategoryMap: Record<string, { label: string; color: string; events: WebhookEventType[] }> = {
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
      width: 320,
      render: (url: string) => (
        <Space>
          <LinkOutlined style={{ color: '#4f46e5' }} />
          <Text copyable ellipsis style={{ maxWidth: 280 }}>{url}</Text>
        </Space>
      ),
    },
    {
      title: '事件类型',
      dataIndex: 'events',
      width: 300,
      render: (events: string[]) => (
        <Space size={4} wrap>
          {events?.slice(0, 3).map((e) => {
            const cat = Object.values(eventCategoryMap).find((c) => c.events.includes(e as WebhookEventType));
            return (
              <Tag key={e} color={cat?.color || 'default'} style={{ borderRadius: 4, fontSize: 11 }}>
                {e}
              </Tag>
            );
          })}
          {events?.length > 3 && <Tag>+{events.length - 3}</Tag>}
        </Space>
      ),
    },
    {
      title: '签名密钥',
      dataIndex: 'secretHint',
      width: 120,
      render: (v: string) => (v ? <Text code>{v}</Text> : <Text type="secondary">未设置</Text>),
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
      width: 160,
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
    <div>
      <Title level={4} style={{ marginBottom: 24 }}>系统设置</Title>

      {/* Webhook 管理 */}
      <Card
        bordered={false}
        title={
          <Space>
            <ApiOutlined style={{ color: '#4f46e5' }} />
            <span>Webhook 配置</span>
          </Space>
        }
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModal(true)}>
            新建 Webhook
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={webhooks}
          loading={loading}
          pagination={false}
        />
      </Card>

      {/* 创建 Webhook 弹窗 */}
      <Modal
        title="新建 Webhook"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreate}
        okText="创建"
        width={580}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="url"
            label="Webhook URL"
            rules={[
              { required: true, message: '请输入 URL' },
              { type: 'url', message: '请输入有效的 URL' },
            ]}
          >
            <Input prefix={<LinkOutlined />} placeholder="https://example.com/webhook" />
          </Form.Item>

          <Form.Item name="secret" label="签名密钥（HMAC-SHA256）">
            <Input.Password placeholder="可选，用于验证消息签名" />
          </Form.Item>

          <Form.Item
            name="events"
            label="订阅事件"
            rules={[{ required: true, message: '至少选择一个事件' }]}
          >
            <Checkbox.Group style={{ width: '100%' }}>
              {Object.entries(eventCategoryMap).map(([catKey, catData]) => (
                <div key={catKey} style={{ marginBottom: 16 }}>
                  <Text strong>
                    <Tag color={catData.color} style={{ marginRight: 8 }}>{catData.label}</Tag>
                  </Text>
                  <Row gutter={8} style={{ marginTop: 8 }}>
                    {catData.events.map((event) => (
                      <Col span={12} key={event}>
                        <Checkbox value={event} style={{ marginBottom: 4 }}>
                          <Text style={{ fontSize: 13 }}>{event}</Text>
                        </Checkbox>
                      </Col>
                    ))}
                  </Row>
                </div>
              ))}
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
