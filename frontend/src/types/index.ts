// ==================== 通用类型 ====================

export interface Result<T> {
  code: number;
  message: string;
  data: T | null;
  timestamp: number;
}

export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ==================== Auth 认证 ====================

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  role?: 'HR' | 'INTERVIEWER';
  verificationCode: string;
}

export interface SendVerificationCodeRequest {
  email: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: UserInfo;
}

export interface UserInfo {
  userId: number;
  username: string;
  email: string;
  role: 'ADMIN' | 'HR' | 'INTERVIEWER';
  dailyAiQuota: number;
  todayAiUsed: number;
}

// ==================== Job 职位 ====================

export interface CreateJobRequest {
  title: string;
  department?: string;
  description: string;
  requirements: string;
  requiredSkills?: string[];
  salaryMin: number;
  salaryMax: number;
  experienceMin?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
}

export interface UpdateJobRequest extends Partial<CreateJobRequest> {
  id: number;
}

export interface JobResponse {
  id: number;
  title: string;
  department: string;
  description: string;
  requirements: string;
  requiredSkills: string[];
  salaryMin: number;
  salaryMax: number;
  salaryRange: string;
  experienceMin: number;
  experienceMax: number;
  experienceRange: string;
  education: string;
  jobType: string;
  status: JobStatus;
  statusDesc: string;
  creatorId: number;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
}

export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

export interface JobQueryParams {
  keyword?: string;
  department?: string;
  jobType?: string;
  education?: string;
  experienceMin?: number;
  salaryMin?: number;
  status?: JobStatus;
  pageNum?: number;
  pageSize?: number;
  orderBy?: 'created_at' | 'salary_max' | 'view_count';
  orderDirection?: 'asc' | 'desc';
}

// ==================== Resume 简历 ====================

export interface ResumeUploadResponse {
  taskId: string;
  resumeId: number;
  duplicated: boolean;
  message: string;
}

export interface BatchUploadResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  items: BatchUploadItem[];
}

export interface BatchUploadItem {
  taskId: string | null;
  resumeId: number | null;
  fileName: string;
  status: 'QUEUED' | 'DUPLICATE' | 'FAILED';
  message: string;
}

export interface TaskStatusResponse {
  status: 'PENDING' | 'PARSING' | 'COMPLETED' | 'FAILED';
  resumeId: number | null;
  candidateId: number | null;
  errorMessage: string | null;
  progress: number | null;
}

export interface Resume {
  id: number;
  userId: number;
  fileName: string;
  filePath: string;
  fileUrl: string;
  fileSize: number;
  fileHash: string;
  fileType: string;
  status: 'PARSING' | 'COMPLETED' | 'FAILED';
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Candidate 候选人 ====================

export interface CandidateResponse {
  id: number;
  resumeId: number;
  name: string;
  phone: string;
  email: string;
  gender: string;
  age: number;
  education: string;
  school: string;
  major: string;
  graduationYear: number;
  workYears: number;
  currentCompany: string;
  currentPosition: string;
  skills: string[];
  workExperience: WorkExperienceItem[];
  projectExperience: ProjectExperienceItem[];
  selfEvaluation: string;
  confidenceScore: number;
  aiSummary: string;
  parsedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkExperienceItem {
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ProjectExperienceItem {
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  technologies: string[];
}

export interface CandidateUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  workYears?: number;
  currentCompany?: string;
  currentPosition?: string;
  skills?: string[];
  selfEvaluation?: string;
}

export interface CandidateQueryParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  education?: string;
  skill?: string;
  minWorkYears?: number;
  maxWorkYears?: number;
  currentPosition?: string;
}

// ==================== Smart Search 智能搜索 ====================

export interface SmartSearchRequest {
  query: string;
  topK?: number;
  minScore?: number;
}

export interface SmartSearchResponse {
  query: string;
  totalMatches: number;
  candidates: MatchedCandidate[];
}

export interface MatchedCandidate {
  candidateId: number;
  name: string;
  matchScore: number;
  currentPosition: string;
  currentCompany: string;
  education: string;
  workYears: number;
  skills: string[];
  aiSummary: string;
}

// ==================== Application 职位申请 ====================

export interface CreateApplicationRequest {
  jobId: number;
  candidateId: number;
  hrNotes?: string;
}

export interface UpdateApplicationStatusRequest {
  status: ApplicationStatus;
  hrNotes?: string;
}

export type ApplicationStatus =
  | 'PENDING'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface ApplicationResponse {
  id: number;
  jobId: number;
  jobTitle: string;
  candidateId: number;
  candidateName: string;
  matchScore: number;
  matchReasons: string[];
  matchCalculatedAt: string;
  status: ApplicationStatus;
  statusDesc: string;
  hrNotes: string;
  appliedAt: string;
  updatedAt: string;
}

export interface ApplicationQueryParams {
  pageNum?: number;
  pageSize?: number;
  jobId?: number;
  candidateId?: number;
  status?: ApplicationStatus;
  orderBy?: 'applied_at' | 'match_score' | 'updated_at';
  orderDirection?: 'asc' | 'desc';
}

// ==================== Interview 面试 ====================

export type InterviewType = 'PHONE' | 'VIDEO' | 'ONSITE' | 'WRITTEN_TEST';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type Recommendation = 'STRONG_YES' | 'YES' | 'NEUTRAL' | 'NO' | 'STRONG_NO';

export interface ScheduleInterviewRequest {
  applicationId: number;
  interviewerId: number;
  round?: number;
  interviewType: InterviewType;
  scheduledAt: string;
  durationMinutes?: number;
}

export interface SubmitFeedbackRequest {
  score: number;
  feedback: string;
  recommendation: Recommendation;
}

export interface InterviewResponse {
  id: number;
  applicationId: number;
  interviewerId: number;
  interviewerName: string;
  round: number;
  interviewType: InterviewType;
  interviewTypeDesc: string;
  scheduledAt: string;
  durationMinutes: number;
  status: InterviewStatus;
  statusDesc: string;
  feedback: string | null;
  score: number | null;
  recommendation: Recommendation | null;
  recommendationDesc: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Webhook ====================

export interface WebhookCreateRequest {
  url: string;
  events: string[];
  description?: string;
}

export interface WebhookResponse {
  id: number;
  url: string;
  events: string[];
  description: string;
  enabled: boolean;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  createdAt: string;
  updatedAt: string;
  secretHint: string;
}

export const WEBHOOK_EVENTS = [
  'resume.uploaded',
  'resume.parse_completed',
  'resume.parse_failed',
  'candidate.created',
  'candidate.updated',
  'application.submitted',
  'application.status_changed',
  'interview.scheduled',
  'interview.completed',
  'interview.cancelled',
  'system.error',
  'system.maintenance',
] as const;

export type WebhookEventType = (typeof WEBHOOK_EVENTS)[number];
