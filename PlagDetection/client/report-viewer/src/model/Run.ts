export interface Run {
  id: string,
  resultName: string
  suffixes: string[],
  languages: string[],
  solutionPaths: string[],
  oldSubmissionPaths: string[],
  whitelistPaths: string,
  userName: string,
  error: string,
  projectName: string,
  isJplagRunning: boolean,
  finished: boolean
  failedToParseSubmissions: object[],
  failedToCompareSubmissions: object[],
  resultHash: string,
}
