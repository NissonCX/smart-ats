import { useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Typography,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  DatePicker,
  message,
  Popconfirm,
  Descriptions,
  Drawer,
  Divider,
} from 'antd';
import {
  PlusOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  StopOutlined,
  EyeOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { motion } from 'framer-motion';
import { interviewApi } from '../../api';
import type { InterviewResponse, InterviewStatus, InterviewType, Recommendation } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Paragraph } = Typography;

const statusConfig: Record<
  InterviewStatus,
  { color: string; label: string; icon: React.ReactNode }
> = {
  SCHEDULED: { color: 'processing', label: '已安排', icon: <ClockCircleOutlined /> },
  COMPLETED: { color: 'success', label: '已完成', icon: <CheckCircleOutlined /> },
  CANCELLED: { color: 'error', label: '已取消', icon: <CloseCircleOutlined /> },
  NO_SHOW: { color: 'warning', label: '未出席', icon: <ExclamationCircleOutlined /> },
};

const typeConfig: Record<InterviewType, string> = {
  PHONE: '电话面试',
  VIDEO: '视频面试',
  ONSITE: '现场面试',
  WRITTEN_TEST: '笔试',
};

const recommendConfig: Record<Recommendation, { color: string; label: string }> = {
  STRONG_YES: { color: '#10b981', label: '强烈推荐' },
  YES: { color: '#22c55e', label: '推荐录用' },
  NEUTRAL: { color: '#f59e0b', label: '中立' },
  NO: { color: '#ef4444', label: '不推荐' },
  STRONG_NO: { color: '#dc2626', label: '强烈不推荐' },
};

export default function InterviewsPage() {
  const [interviews, setInterviews] = useState<InterviewResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [scheduleModal, setScheduleModal] = useState(false);
  const [feedbackModal, setFeedbackModal] = useState<InterviewResponse | null>(null);
  const [detailDrawer, setDetailDrawer] = useState<InterviewResponse | null>(null);
  const [applicationIdFilter, setApplicationIdFilter] = useState<number | undefined>();
  const [form] = Form.useForm();
  const [feedbackForm] = Form.useForm();

  useEffect(() => {
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  }, [applicationIdFilter]);

  const loadByApplication = async (appId: number) => {
    setLoading(true);
    try {
      const { data } = await interviewApi.listByApplication(appId);
      setInterviews(data.data || []);
    } finally {
      setLoading(false);
    }
  };

  const handleSchedule = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      scheduledAt: values.scheduledAt.format('YYYY-MM-DD HH:mm:ss'),
    };
    await interviewApi.schedule(payload);
    message.success('面试已安排');
    setScheduleModal(false);
    form.resetFields();
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  };

  const handleFeedback = async () => {
    const values = await feedbackForm.validateFields();
    await interviewApi.submitFeedback(feedbackModal!.id, values);
    message.success('反馈已提交');
    setFeedbackModal(null);
    feedbackForm.resetFields();
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  };

  const handleCancel = async (id: number) => {
    await interviewApi.cancel(id);
    message.success('面试已取消');
    if (applicationIdFilter) loadByApplication(applicationIdFilter);
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '申请 ID', dataIndex: 'applicationId', width: 80 },
    {
      title: '轮次',
      dataIndex: 'round',
      width: 70,
      render: (v: number) => `第 ${v} 轮`,
    },
    {
      title: '类型',
      dataIndex: 'interviewType',
      width: 100,
      render: (type: InterviewType) => typeConfig[type] || type,
    },
    { title: '面试官', dataIndex: 'interviewerName', width: 100 },
    {
      title: '面试时间',
      dataIndex: 'scheduledAt',
      width: 160,
      render: (v: string) => (
        <span className="text-gray-600">
          <CalendarOutlined className="text-cyan-600 mr-1" />
          {v}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: InterviewStatus) => {
        const cfg = statusConfig[status];
        return cfg ? (
          <Tag icon={cfg.icon} color={cfg.color}>
            {cfg.label}
          </Tag>
        ) : (
          status
        );
      },
    },
    {
      title: '评分',
      dataIndex: 'score',
      width: 80,
      render: (score: number) =>
        score !== undefined && score !== null ? (
          <span className="font-semibold">{score} 分</span>
        ) : (
          '-'
        ),
    },
    {
      title: '推荐',
      dataIndex: 'recommendation',
      width: 120,
      render: (rec: Recommendation) => {
        if (!rec) return '-';
        const cfg = recommendConfig[rec];
        return cfg ? (
          <span style={{ color: cfg.color }} className="font-semibold">
            {cfg.label}
          </span>
        ) : (
          rec
        );
      },
    },
    {
      title: '操作',
      width: 160,
      render: (_: unknown, record: InterviewResponse) => (
        <Space size={4} wrap>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setDetailDrawer(record)}
          >
            详情
          </Button>
          {record.status === 'SCHEDULED' && (
            <>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => {
                  setFeedbackModal(record);
                  feedbackForm.resetFields();
                }}
              >
                反馈
              </Button>
              <Popconfirm
                title="确定取消面试？"
                onConfirm={() => handleCancel(record.id)}
              >
                <Button type="link" size="small" icon={<StopOutlined />} danger>
                  取消
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">面试管理</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setScheduleModal(true)}
        >
          安排面试
        </Button>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* 筛选 */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center">
              <InputNumber
                placeholder="输入申请 ID 查询面试记录"
                min={1}
                className="w-full sm:w-64"
                value={applicationIdFilter}
                onChange={(v) => setApplicationIdFilter(v ?? undefined)}
              />
              <Button
                type="primary"
                onClick={() =>
                  applicationIdFilter && loadByApplication(applicationIdFilter)
                }
              >
                查询
              </Button>
            </div>
          </Card>
        </motion.div>

        {/* 表格 */}
        <motion.div variants={staggerItem}>
          <Card bordered={false}>
            {applicationIdFilter ? (
              <Table
                rowKey="id"
                columns={columns}
                dataSource={interviews}
                loading={loading}
                scroll={{ x: true }}
                pagination={false}
              />
            ) : (
              <div className="text-center py-12 text-gray-400">
                <CalendarOutlined className="text-5xl mb-4 block" />
                <p>请输入申请 ID 查询面试记录</p>
              </div>
            )}
          </Card>
        </motion.div>
      </motion.div>

      {/* 安排面试弹窗 */}
      <Modal
        title="安排面试"
        open={scheduleModal}
        onCancel={() => setScheduleModal(false)}
        onOk={handleSchedule}
        okText="确认安排"
        width={520}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item name="applicationId" label="申请 ID" rules={[{ required: true }]}>
            <InputNumber min={1} className="w-full" placeholder="职位申请 ID" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="round" label="面试轮次" rules={[{ required: true }]}>
              <InputNumber min={1} max={10} className="w-full" />
            </Form.Item>
            <Form.Item name="interviewType" label="面试类型" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'PHONE', label: '电话面试' },
                  { value: 'VIDEO', label: '视频面试' },
                  { value: 'ONSITE', label: '现场面试' },
                  { value: 'WRITTEN_TEST', label: '笔试' },
                ]}
              />
            </Form.Item>
          </div>
          <Form.Item name="scheduledAt" label="面试时间" rules={[{ required: true }]}>
            <DatePicker showTime className="w-full" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="interviewerId" label="面试官 ID" rules={[{ required: true }]}>
              <InputNumber min={1} className="w-full" />
            </Form.Item>
            <Form.Item name="durationMinutes" label="时长（分钟）">
              <InputNumber min={15} max={480} className="w-full" placeholder="如：60" />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      {/* 反馈弹窗 */}
      <Modal
        title={`提交面试反馈 - 第 ${feedbackModal?.round || ''} 轮`}
        open={!!feedbackModal}
        onCancel={() => setFeedbackModal(null)}
        onOk={handleFeedback}
        okText="提交反馈"
        width={520}
      >
        <Form form={feedbackForm} layout="vertical" className="mt-4">
          <Form.Item name="score" label="面试评分 (1-100)" rules={[{ required: true }]}>
            <InputNumber min={1} max={100} className="w-full" />
          </Form.Item>
          <Form.Item name="recommendation" label="推荐意见" rules={[{ required: true }]}>
            <Select
              options={Object.entries(recommendConfig).map(([k, v]) => ({
                value: k,
                label: v.label,
              }))}
            />
          </Form.Item>
          <Form.Item name="feedback" label="详细反馈" rules={[{ required: true }]}>
            <Input.TextArea rows={4} placeholder="请描述面试评价..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情抽屉 */}
      <Drawer
        title={`面试详情 #${detailDrawer?.id || ''}`}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={480}
      >
        {detailDrawer && (
          <div className="space-y-6">
            <Descriptions column={2}>
              <Descriptions.Item label="申请 ID">
                {detailDrawer.applicationId}
              </Descriptions.Item>
              <Descriptions.Item label="轮次">
                第 {detailDrawer.round} 轮
              </Descriptions.Item>
              <Descriptions.Item label="类型">
                {typeConfig[detailDrawer.interviewType]}
              </Descriptions.Item>
              <Descriptions.Item label="面试官">
                {detailDrawer.interviewerName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="时间">
                {detailDrawer.scheduledAt}
              </Descriptions.Item>
              <Descriptions.Item label="时长">
                {detailDrawer.durationMinutes
                  ? `${detailDrawer.durationMinutes} 分钟`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态" span={2}>
                <Tag color={statusConfig[detailDrawer.status]?.color}>
                  {statusConfig[detailDrawer.status]?.label}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            {detailDrawer.score !== undefined &&
              detailDrawer.score !== null && (
              <>
                <Divider className="my-3" />
                <h4 className="font-semibold text-gray-900 mb-3">面试反馈</h4>
                <Descriptions column={2}>
                  <Descriptions.Item label="评分">
                    <span className="text-lg font-semibold">{detailDrawer.score}</span>{' '}
                    / 100
                  </Descriptions.Item>
                  <Descriptions.Item label="推荐">
                    {detailDrawer.recommendation && (
                      <span
                        style={{
                          color:
                            recommendConfig[detailDrawer.recommendation]?.color,
                        }}
                        className="font-semibold"
                      >
                        {recommendConfig[detailDrawer.recommendation]?.label}
                      </span>
                    )}
                  </Descriptions.Item>
                </Descriptions>
                {detailDrawer.feedback && (
                  <div>
                    <p className="font-semibold text-gray-900 mb-2">详细反馈</p>
                    <Paragraph className="whitespace-pre-wrap text-gray-600">
                      {detailDrawer.feedback}
                    </Paragraph>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </Drawer>
    </PageTransition>
  );
}
