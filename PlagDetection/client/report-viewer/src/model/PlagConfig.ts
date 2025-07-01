interface PlagConfig {
  id?: string;
  name: string;
  subjectName: string;
  resourcesTTL: number;
  clientBackendURL: string;
  projects: StudentProject[];
  scheduleType: ScheduleType;
  userName: string;
}

interface StudentProject {
  name: string;
  resources: Resource[];
  suffixes: Set<string>;
  languages: Set<string>;
  resultHashes: Set<string>;
}

interface Resource {
  exceptionMessage?: string;
  resourceType: ResourceType;
  lastUpdate?: Date;
  path: string;
  academicYear: string;
  hash?: string;
  hasBeenChanged?: boolean;
}

interface GitResource extends Resource {
  repoURL: string;
  branch: string;
}

enum ScheduleType {
  DAILY = 1,
  WEEKLY = 7,
  EVERY_TWO_WEEKS = 14,
  MONTHLY = 30,
}

export enum ResourceType {
  EDGAR = "EDGAR",
  GIT = "GIT",
  ZIP = "ZIP",
}
