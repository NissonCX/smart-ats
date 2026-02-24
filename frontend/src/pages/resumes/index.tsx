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
  Divider,
} from 'antd';
import {
  UploadOutlined,
  CloudUploadOutlined,
  FileTextOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import { resumeApi } from '../../api';
import type { Resume, TaskStatusResponse } from '../../types';

const { Title, Text } = Typography;
const { Dragger } = Upload;

const parseStatusConfig: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
  PENDING: { color: 'default', icon: <ClockCircleOutlined />, label: '等待解析' },
  PARSING: { color: 'processing', icon: <SyncOutlined spin />, label: '解析中' },
  COMPLETED: { color: 'success', icon: <CheckCircleOutlined />, label: '解析完成' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, label: '解析失败' },
};

const fileIcon = (name: string) => {
  const ext = name?.split('.').pop()?.toLowerCase();
  if (ext === 'pdf') return <FilePdfOutlined style={{ color: '#ef4444', fontSize: 18 }} />;
  if (ext === 'doc' || ext === 'docx') return <FileWordOutlined style={{ color: '#3b82f6', fontSize: 18 }} />;
  return <FileTextOutlined style={{ fontSize: 18 }} />;
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
    // 轮询解析状态
    const pendingTasks = tasks.filter((t) => t.status === 'PENDING' || t.status === 'PARSING');
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
                progress: info.status === 'COMPLETED' ? 100 : info.status === 'PARSING' ? 60 : 20,
                candidateId: info.candidateId ?? undefined,
                errorMessage: info.errorMessage ?? undefined,
              };
            } catch {
              return task;
            }
          })
        );
        setTasks(updated);
        const stillPending = updated.filter((t) => t.status === 'PENDING' || t.status === 'PARSING');
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

  const handleUpload = async (fileList: UploadFile[]) => {
    if (fileList.length === 0) return;
    setUploading(true);
    try {
      if (fileList.length === 1) {
        const file = fileList[0].originFileObj as File;
        const { data } = await resumeApi.upload(file);
        const resp = data.data;
        if (resp) {
          setTasks((prev) => [
            ...prev,
            { taskId: resp.taskId, fileName: file.name, status: 'PENDING', progress: 20 },
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
          if (resp.failedCount > 0) {
            message.warning(`${resp.failedCount} 个文件上传失败`);
          }
        }
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const columns = [
    {
      title: '文件',
      dataIndex: 'originalFilename',
      width: 280,
      render: (name: string) => (
        <Space>
          {fileIcon(name)}
          <Text ellipsis style={{ maxWidth: 220 }}>{name}</Text>
        </Space>
      ),
    },
    {
      title: '文件大小',
      dataIndex: 'fileSize',
      width: 100,
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
      width: 120,
      render: (status: string) => {
        const cfg = parseStatusConfig[status] || parseStatusConfig.PENDING;
        return (
          <Tag icon={cfg.icon} color={cfg.color}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '上传者',
      dataIndex: 'uploaderName',
      width: 100,
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      width: 160,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>简历管理</Title>
        <Button type="primary" icon={<UploadOutlined />} onClick={() => setUploadModalOpen(true)}>
          上传简历
        </Button>
      </div>

      {/* 解析任务进度 */}
      {tasks.length > 0 && (
        <Card
          bordered={false}
          title={
            <Space>
              <SyncOutlined spin={tasks.some((t) => t.status === 'PARSING' || t.status === 'PENDING')} />
              解析任务
              <Badge
                count={tasks.filter((t) => t.status === 'PENDING' || t.status === 'PARSING').length}
                style={{ backgroundColor: '#4f46e5' }}
              />
            </Space>
          }
          style={{ marginBottom: 16 }}
          extra={
            <Button
              size="small"
              type="link"
              onClick={() => setTasks([])}
              disabled={tasks.some((t) => t.status === 'PENDING' || t.status === 'PARSING')}
            >
              清除
            </Button>
          }
        >
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 }}>
            {tasks.map((task) => {
              const cfg = parseStatusConfig[task.status] || parseStatusConfig.PENDING;
              return (
                <div
                  key={task.taskId}
                  style={{
                    padding: '12px 16px',
                    borderRadius: 8,
                    border: '1px solid #e2e8f0',
                    background: '#fafbfc',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <Space>
                      {fileIcon(task.fileName)}
                      <Text ellipsis style={{ maxWidth: 160 }}>{task.fileName}</Text>
                    </Space>
                    <Tag color={cfg.color} style={{ marginRight: 0 }}>{cfg.label}</Tag>
                  </div>
                  <Progress
                    percent={task.progress}
                    size="small"
                    status={task.status === 'FAILED' ? 'exception' : task.status === 'COMPLETED' ? 'success' : 'active'}
                  />
                  {task.errorMessage && (
                    <Text type="danger" style={{ fontSize: 12 }}>{task.errorMessage}</Text>
                  )}
                </div>
              );
            })}
          </div>
        </Card>
      )}

      {/* 列表 */}
      <Card bordered={false}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={resumes}
          loading={loading}
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

      {/* 上传弹窗 */}
      <Modal
        title="上传简历"
        open={uploadModalOpen}
        onCancel={() => setUploadModalOpen(false)}
        footer={null}
        width={520}
      >
        <Dragger
          name="files"
          multiple
          accept=".pdf,.doc,.docx"
          maxCount={20}
          showUploadList
          beforeUpload={() => false}
          onChange={() => {
            // 仅收集文件，不自动上传
          }}
          customRequest={() => {}}
          style={{ marginBottom: 16 }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined style={{ color: '#4f46e5', fontSize: 48 }} />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p className="ant-upload-hint">
            支持 PDF、DOC、DOCX 格式，单个文件不超过 10MB，批量最多 20 个
          </p>
        </Dragger>
        <Divider />
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Space>
            <Button onClick={() => setUploadModalOpen(false)}>取消</Button>
            <UploadButton
              uploading={uploading}
              onUpload={(files) => {
                handleUpload(files);
                setUploadModalOpen(false);
              }}
            />
          </Space>
        </div>
      </Modal>
    </div>
  );
}

/** Upload button that grabs files from sibling Dragger */
function UploadButton({
  uploading,
  onUpload,
}: {
  uploading: boolean;
  onUpload: (files: UploadFile[]) => void;
}) {
  return (
    <Upload
      accept=".pdf,.doc,.docx"
      multiple
      maxCount={20}
      showUploadList={false}
      beforeUpload={() => false}
      onChange={({ fileList }) => onUpload(fileList)}
    >
      <Button type="primary" icon={<CloudUploadOutlined />} loading={uploading}>
        选择文件并上传
      </Button>
    </Upload>
  );
}
