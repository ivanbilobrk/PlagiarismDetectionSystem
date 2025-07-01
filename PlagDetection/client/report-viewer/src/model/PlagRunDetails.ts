import type { FailedToParseSubmission } from '@/model/FailedToParseSubmission.ts'
import type { FailedToCompareSubmissions } from '@/model/FailedToCompareSubmissions.ts'

export interface PlagRunDetails {
  id?: string;
  resultHash: string;
  error?: string;
  finished?: boolean;
  submissionTotal?: number;
  comparisonTotal?: number;
  failedToParseSubmissions?: FailedToParseSubmission[];
  failedToCompareSubmissions?: FailedToCompareSubmissions[];
  isPaused?: boolean;
  processedNormalizations: number;
  processedParsings: number;
  processedComparisons: number;
  currentlyProcessingSubmissions?: string[];
}
