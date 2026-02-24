import request from './request';
import type {
  Result,
  Page,
  Resume,
  ResumeUploadResponse,
  BatchUploadResponse,
  TaskStatusResponse,
} from '../types';

export const resumeApi = {
  upload: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<Result<ResumeUploadResponse>>('/resumes/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    });
  },

  batchUpload: (files: File[]) => {
    const formData = new FormData();
    files.forEach((f) => formData.append('files', f));
    return request.post<Result<BatchUploadResponse>>('/resumes/batch-upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    });
  },

  getTaskStatus: (taskId: string) =>
    request.get<Result<TaskStatusResponse>>(`/resumes/tasks/${taskId}`),

  getById: (id: number) =>
    request.get<Result<Resume>>(`/resumes/${id}`),

  list: (params?: { page?: number; size?: number }) =>
    request.get<Result<Page<Resume>>>('/resumes', { params }),
};
