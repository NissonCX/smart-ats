import { useCallback, useEffect, useState } from 'react';
import {
  Table,
  Card,
  Space,
  Tag,
  Select,
  Input,
  DatePicker,
  Typography,
  Drawer,
  Descriptions,
  Badge,
  InputNumber,
} from 'antd';
import {
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SearchOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { motion } from 'framer-motion';
import dayjs from 'dayjs';
import { auditApi } from '../../api';
import type { AuditLogResponse, AuditLogQueryRequest } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;
const { RangePicker } = DatePicker;

const moduleOptions = [
  { value: '职位管理', label: '职位管理' },
  { value: '简历管理', label: '简历管理' },
  { value: '候选人管理', label: '候选人管理' },
  { value: '职位申请', label: '职位申请' },
  { value: '面试管理', label: '面试管理' },
  { value: 'WEBHOOK', label: 'Webhook' },
  { value: 'ANALYTICS', label: '招聘分析' },
];

const statusConfig = {
  SUCCESS: { color: 'success', icon: <CheckCircleOutlined />, label: '成功' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, label: '失败' },
} as const;

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLogResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<AuditLogQueryRequest>({ pageNum: 1, pageSize: 20 });
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedLog, setSelectedLog] = useState<AuditLogResponse | null>(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await auditApi.list(query);
      setLogs(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const handleViewDetail = (record: AuditLogResponse) => {
    setSelectedLog(record);
    setDrawerOpen(true);
  };

  const columns: ColumnsType<AuditLogResponse> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => (
        <Space size={4}>
          <ClockCircleOutlined className="text-gray-400" />
          <Text className="text-xs">{v}</Text>
        </Space>
      ),
    },
    {
      title: '用户',
      dataIndex: 'username',
      width: 100,
      ellipsis: true,
    },
    {
      title: '模块',
      dataIndex: 'module',
      width: 100,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '操作',
      dataIndex: 'operation',
      width: 120,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (status: 'SUCCESS' | 'FAILED') => {
        const cfg = statusConfig[status];
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '耗时',
      dataIndex: 'duration',
      width: 80,
      render: (v: number) => {
        const color = v > 1000 ? 'text-red-500' : v > 300 ? 'text-amber-500' : 'text-green-600';
        return <span className={`font-mono text-xs ${color}`}>{v}ms</span>;
      },
    },
    {
      title: 'IP',
      dataIndex: 'requestIp',
      width: 120,
      render: (v: string) => <Text className="font-mono text-xs">{v}</Text>,
    },
    {
      title: '操作',
      width: 60,
      render: (_: unknown, record: AuditLogResponse) => (
        <EyeOutlined
          className="text-gray-500 hover:text-blue-500 cursor-pointer transition-colors"
          onClick={() => handleViewDetail(record)}
        />
      ),
    },
  ];

  const handleDateChange = (_: unknown, dateStrings: [string, string]) => {
    setQuery((q) => ({
      ...q,
      startTime: dateStrings[0] ? `${dateStrings[0]} 00:00:00` : undefined,
      endTime: dateStrings[1] ? `${dateStrings[1]} 23:59:59` : undefined,
      pageNum: 1,
    }));
  };

  // Statistics
  const successCount = logs.filter((l) => l.status === 'SUCCESS').length;
  const failedCount = logs.filter((l) => l.status === 'FAILED').length;
  const avgDuration = logs.length
    ? Math.round(logs.reduce((sum, l) => sum + l.duration, 0) / logs.length)
    : 0;

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-gray-900 m-0">审计日志</h2>
        <Text type="secondary" className="text-sm">
          共 {total} 条记录
        </Text>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* 统计卡片 */}
        <motion.div variants={staggerItem}>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 mb-4">
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-gray-900">{total}</div>
                <div className="text-xs text-gray-500 mt-1">总记录数</div>
              </div>
            </Card>
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-green-600">{successCount}</div>
                <div className="text-xs text-gray-500 mt-1">成功（当页）</div>
              </div>
            </Card>
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-red-500">{failedCount}</div>
                <div className="text-xs text-gray-500 mt-1">失败（当页）</div>
              </div>
            </Card>
            <Card bordered={false}>
              <div className="text-center">
                <div className="text-2xl font-bold text-blue-600">{avgDuration}ms</div>
                <div className="text-xs text-gray-500 mt-1">平均耗时（当页）</div>
              </div>
            </Card>
          </div>
        </motion.div>

        {/* 筛选 */}
        <motion.div variants={staggerItem}>
          <Card bordered={false} className="mb-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
              <div className="md:col-span-2">
                <Select
                  placeholder="模块"
                  allowClear
                  className="w-full"
                  options={moduleOptions}
                  onChange={(v) => setQuery((q) => ({ ...q, module: v, pageNum: 1 }))}
                />
              </div>
              <div className="md:col-span-2">
                <Select
                  placeholder="状态"
                  allowClear
                  className="w-full"
                  options={[
                    { value: 'SUCCESS', label: '成功' },
                    { value: 'FAILED', label: '失败' },
                  ]}
                  onChange={(v) => setQuery((q) => ({ ...q, status: v, pageNum: 1 }))}
                />
              </div>
              <div className="md:col-span-2">
                <InputNumber
                  placeholder="用户 ID"
                  min={1}
                  className="w-full"
                  onChange={(v) =>
                    setQuery((q) => ({ ...q, userId: v ?? undefined, pageNum: 1 }))
                  }
                />
              </div>
              <div className="md:col-span-3">
                <RangePicker
                  className="w-full"
                  onChange={handleDateChange}
                  placeholder={['开始日期', '结束日期']}
                />
              </div>
              <div className="md:col-span-3">
                <Input
                  placeholder="搜索描述关键词..."
                  prefix={<SearchOutlined />}
                  allowClear
                  onPressEnter={(e) =>
                    setQuery((q) => ({
                      ...q,
                      keyword: (e.target as HTMLInputElement).value || undefined,
                      pageNum: 1,
                    }))
                  }
                  onChange={(e) => {
                    if (!e.target.value) {
                      setQuery((q) => ({ ...q, keyword: undefined, pageNum: 1 }));
                    }
                  }}
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
              dataSource={logs}
              loading={loading}
              scroll={{ x: 1000 }}
              size="small"
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

      {/* 详情抽屉 */}
      <Drawer
        title="审计日志详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
      >
        {selectedLog && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="ID">{selectedLog.id}</Descriptions.Item>
            <Descriptions.Item label="时间">{selectedLog.createdAt}</Descriptions.Item>
            <Descriptions.Item label="用户">
              {selectedLog.username} (ID: {selectedLog.userId})
            </Descriptions.Item>
            <Descriptions.Item label="模块">
              <Tag>{selectedLog.module}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="操作">
              <Tag color="blue">{selectedLog.operation}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="描述">{selectedLog.description}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Badge
                status={selectedLog.status === 'SUCCESS' ? 'success' : 'error'}
                text={selectedLog.status === 'SUCCESS' ? '成功' : '失败'}
              />
            </Descriptions.Item>
            {selectedLog.errorMessage && (
              <Descriptions.Item label="错误信息">
                <Text type="danger">{selectedLog.errorMessage}</Text>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="执行耗时">
              <span
                className={`font-mono ${selectedLog.duration > 1000 ? 'text-red-500' : selectedLog.duration > 300 ? 'text-amber-500' : 'text-green-600'}`}
              >
                {selectedLog.duration}ms
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="请求方法">
              <Tag color="purple">{selectedLog.requestMethod}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="请求路径">
              <Text className="font-mono text-xs" copyable>
                {selectedLog.requestUrl}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Java 方法">
              <Text className="font-mono text-xs" ellipsis>
                {selectedLog.method}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="请求 IP">
              <Text className="font-mono">{selectedLog.requestIp}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="请求参数">
              <div className="max-h-40 overflow-auto">
                <pre className="text-xs bg-gray-50 p-2 rounded m-0 whitespace-pre-wrap">
                  {(() => {
                    try {
                      return JSON.stringify(
                        JSON.parse(selectedLog.requestParams || '{}'),
                        null,
                        2
                      );
                    } catch {
                      return selectedLog.requestParams || '-';
                    }
                  })()}
                </pre>
              </div>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </PageTransition>
  );
}
