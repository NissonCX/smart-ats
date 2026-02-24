import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Table,
  Button,
  Card,
  Space,
  Tag,
  Upload,
  Modal,
  Progress,
  Typography,
  message,
  Badge,
} from 'antd';
import {
  UploadOutlined,
  FileTextOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import { motion } from 'framer-motion';
import { resumeApi } from '../../api';
import type { Resume, TaskStatusResponse } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text, Title } = Typography;

const parseStatusConfig: Record<
  string,
  { color: string; icon: React.ReactNode; label: string }
> = {
  PENDING: { color: 'default', icon: <ClockCircleOutlined />, label: '等待解析' },
  PARSING: { color: 'processing', icon: <SyncOutlined spin />, label: '解析中' },
  COMPLETED: { color: 'success', icon: <CheckCircleOutlined />, label: '解析完成' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, label: '解析失败' },
};

const fileIcon = (name: string) => {
  const ext = name?.split('.').pop()?.toLowerCase();
  if (ext === 'pdf') return <FilePdfOutlined className="text-red-500 text-lg" />;
  if (ext === 'doc' || ext === 'docx')
    return <FileWordOutlined className="text-blue-500 text-lg" />;
  return <FileTextOutlined className="text-gray-400 text-lg" />;
};

interface UploadTask {
  taskId: string;
  fileName: string;
  status: string;
  progress: number;
  candidateId?: number;
  errorMessage?: string;
}

export default function ResumesPage() {
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [tasks, setTasks] = useState<UploadTask[]>([]);
  const [uploading, setUploading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadResumes = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await resumeApi.list({ page: pageNum, size: pageSize });
      setResumes(data.data?.records || []);
      setTotal(data.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, pageSize]);

  useEffect(() => {
    loadResumes();
  }, [loadResumes]);

  useEffect(() => {
    const pendingTasks = tasks.filter(
      (t) => t.status === 'PENDING' || t.status === 'PARSING'
    );
    if (pendingTasks.length > 0 && !pollingRef.current) {
      pollingRef.current = setInterval(async () => {
        const updated = await Promise.all(
          tasks.map(async (task) => {
            if (task.status === 'COMPLETED' || task.status === 'FAILED') return task;
            try {
              const { data } = await resumeApi.getTaskStatus(task.taskId);
              const info = data.data as TaskStatusResponse;
              return {
                ...task,
                status: info.status,
                progress:
                  info.status === 'COMPLETED'
                    ? 100
                    : info.status === 'PARSING'
                      ? 60
                      : 20,
                candidateId: info.candidateId ?? undefined,
                errorMessage: info.errorMessage ?? undefined,
              };
            } catch {
              return task;
            }
          })
        );
        setTasks(updated);
        const stillPending = updated.filter(
          (t) => t.status === 'PENDING' || t.status === 'PARSING'
        );
        if (stillPending.length === 0) {
          clearInterval(pollingRef.current!);
          pollingRef.current = null;
          loadResumes();
        }
      }, 3000);
    }
    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [tasks, loadResumes]);

  const handleUpload = async () => {
    if (fileList.length === 0) {
      message.warning('请先选择要上传的文件');
      return;
    }
    setUploading(true);
    try {
      if (fileList.length === 1) {
        const file = fileList[0].originFileObj as File;
        const { data } = await resumeApi.upload(file);
        const resp = data.data;
        if (resp) {
          setTasks((prev) => [
            ...prev,
            {
              taskId: resp.taskId,
              fileName: file.name,
              status: 'PENDING',
              progress: 20,
            },
          ]);
          message.success(`${file.name} 上传成功，开始解析`);
        }
      } else {
        const files = fileList.map((f) => f.originFileObj as File);
        const { data } = await resumeApi.batchUpload(files);
        const resp = data.data;
        if (resp) {
          const newTasks = resp.items
            .filter((r) => r.status === 'QUEUED' && r.taskId)
            .map((r) => ({
              taskId: r.taskId!,
              fileName: r.fileName,
              status: 'PENDING' as const,
              progress: 20,
            }));
          setTasks((prev) => [...prev, ...newTasks]);
          message.success(`${resp.successCount} 个文件上传成功`);
          if (resp.failedCount > 0)
            message.warning(`${resp.failedCount} 个文件上传失败`);
        }
      }
      // 成功后关闭弹窗并清空文件列表
      setUploadModalOpen(false);
      setFileList([]);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const columns = [
    {
      title: '文件名',
      dataIndex: 'originalFilename',
      width: 240,
      render: (name: string) => (
        <div className="flex items-center gap-2">
          {fileIcon(name)}
          <Text ellipsis style={{ maxWidth: 180 }} className="font-medium">
            {name}
          </Text>
        </div>
      ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 90,
      render: (size: number) => {
        if (!size) return '-';
        if (size < 1024) return `${size} B`;
        if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
        return `${(size / 1024 / 1024).toFixed(1)} MB`;
      },
    },
    {
      title: '解析状态',
      dataIndex: 'parseStatus',
      width: 110,
      render: (status: string) => {
        const cfg = parseStatusConfig[status] || parseStatusConfig.PENDING;
        return <Tag icon={cfg.icon} color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '上传者', dataIndex: 'uploaderName', width: 100 },
    { title: '上传时间', dataIndex: 'createdAt', width: 160 },
  ];

  return (
    <PageTransition>
      {/* Page Header */}
      <div className="mb-6">
        <Title level={2} className="m-0 text-gray-900">
          简历管理
        </Title>
        <p className="text-gray-500 mt-1">上传简历，AI 自动解析候选人信息</p>
      </div>

      <motion.div variants={staggerContainer} initial="initial" animate="animate">
        {/* 解析任务进度 */}
        {tasks.length > 0 && (
          <motion.div variants={staggerItem}>
            <Card
              bordered={false}
              className="mb-5 !border-0 !shadow-sm"
              styles={{
                body: { padding: '16px 20px' },
              }}
              title={
                <Space className="text-sm">
                  <SyncOutlined
                    spin={tasks.some(
                      (t) => t.status === 'PARSING' || t.status === 'PENDING'
                    )}
                    className="text-cyan-600"
                  />
                  <span className="font-medium text-gray-700">解析任务</span>
                  <Badge
                    count={
                      tasks.filter(
                        (t) => t.status === 'PENDING' || t.status === 'PARSING'
                      ).length
                    }
                    className="[&_.ant-badge-count]:!bg-cyan-600"
                  />
                </Space>
              }
              extra={
                <Button
                  size="small"
                  type="text"
                  onClick={() => setTasks([])}
                  disabled={tasks.some(
                    (t) => t.status === 'PENDING' || t.status === 'PARSING'
                  )}
                  className="!text-gray-500 hover:!text-gray-700"
                >
                  清除
                </Button>
              }
            >
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {tasks.map((task) => {
                  const cfg = parseStatusConfig[task.status] || parseStatusConfig.PENDING;
                  return (
                    <div
                      key={task.taskId}
                      className="p-3 rounded-lg border border-gray-100 bg-gray-50"
                    >
                      <div className="flex justify-between items-center mb-2">
                        <Space className="!gap-1.5">
                          {fileIcon(task.fileName)}
                          <Text ellipsis style={{ maxWidth: 120 }} className="text-sm">
                            {task.fileName}
                          </Text>
                        </Space>
                        <Tag color={cfg.color} className="mr-0 !m-0">
                          {cfg.label}
                        </Tag>
                      </div>
                      <Progress
                        percent={task.progress}
                        size="small"
                        strokeColor={{ '0%': '#06B6D4', '100%': '#10B981' }}
                        status={
                          task.status === 'FAILED'
                            ? 'exception'
                            : task.status === 'COMPLETED'
                              ? 'success'
                              : 'active'
                        }
                      />
                      {task.errorMessage && (
                        <Text type="danger" className="text-xs mt-1 block">
                          {task.errorMessage}
                        </Text>
                      )}
                    </div>
                  );
                })}
              </div>
            </Card>
          </motion.div>
        )}

        {/* 主内容区 - 左侧列表，右侧上传 */}
        <div className="grid grid-cols-1 xl:grid-cols-4 gap-5">
          {/* 简历列表 */}
          <motion.div variants={staggerItem} className="xl:col-span-3">
            <Card
              bordered={false}
              className="!border-0 !shadow-sm h-full"
              title={
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-700">简历列表</span>
                  <Text type="secondary" className="text-sm">
                    共 {total} 份
                  </Text>
                </div>
              }
            >
              <Table
                rowKey="id"
                columns={columns}
                dataSource={resumes}
                loading={loading}
                scroll={{ x: true }}
                pagination={{
                  current: pageNum,
                  pageSize,
                  total,
                  showSizeChanger: true,
                  showTotal: (t) => `共 ${t} 条`,
                  onChange: (p, s) => {
                    setPageNum(p);
                    setPageSize(s);
                  },
                }}
              />
            </Card>
          </motion.div>

          {/* 上传区域 */}
          <motion.div variants={staggerItem} className="xl:col-span-1">
            <Card
              bordered={false}
              className="!border-0 !shadow-sm"
              title={<span className="font-medium text-gray-700">快速上传</span>}
            >
              <div className="space-y-4">
                {/* 上传按钮 */}
                <Upload.Dragger
                  accept=".pdf,.doc,.docx"
                  multiple
                  maxCount={20}
                  fileList={fileList}
                  onChange={({ fileList }) => setFileList(fileList)}
                  beforeUpload={() => false}
                  className="!bg-gray-50"
                >
                  <div className="py-6">
                    <CloudUploadOutlined className="text-4xl text-cyan-600 mb-3" />
                    <p className="text-gray-700 font-medium mb-1">点击或拖拽上传</p>
                    <p className="text-gray-400 text-xs">
                      PDF / DOC / DOCX，单文件最大 10MB
                    </p>
                  </div>
                </Upload.Dragger>

                {/* 操作按钮 */}
                <Space className="w-full" direction="vertical" className="!w-full">
                  <Button
                    type="primary"
                    icon={<UploadOutlined />}
                    onClick={handleUpload}
                    loading={uploading}
                    disabled={fileList.length === 0}
                    block
                    size="large"
                    className="!h-12"
                  >
                    {fileList.length > 0 ? `上传 ${fileList.length} 个文件` : '上传简历'}
                  </Button>
                  {fileList.length > 0 && (
                    <Button
                      icon={<DeleteOutlined />}
                      onClick={() => setFileList([])}
                      disabled={uploading}
                      block
                    >
                      清空选择
                    </Button>
                  )}
                </Space>

                {/* 说明文字 */}
                <div className="bg-gray-50 rounded-lg p-3 text-xs text-gray-500 space-y-1">
                  <p>• AI 自动提取候选人信息</p>
                  <p>• 支持批量上传，最多 20 个文件</p>
                  <p>• 上传后自动开始解析</p>
                </div>
              </div>
            </Card>
          </motion.div>
        </div>
      </motion.div>
    </PageTransition>
  );
}
