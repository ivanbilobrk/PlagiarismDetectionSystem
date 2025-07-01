import type { JPlagSolutionRun } from '@/model/JPlagSolutionRun.ts'
import JSZip from 'jszip'
import type { PlagSuspendedStatus } from '@/model/PlagSuspendedStatus.ts'

export async function loadFileStructure(username: string) {
  const response = await fetch(`${backendBaseUrl}fileStructure?userName=${username}`)
  if (!response.ok) {
    return
  }
  return await response.json()
}

export async function deleteFile(filePaths: string[]) {
  const response = await fetch(`${backendBaseUrl}delete`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(filePaths)
  })
  if (!response.ok) {
    throw new Error('Unable to delete file from server.')
  }
}

export async function uploadFile(
  zipFilePath,
  filter: string[],
  userName: string,
  resourceName: string
) {
  const formData = new FormData()

  const zipFile = new File([zipFilePath], `${resourceName}.zip`, { type: 'application/zip' })
  formData.append('zipFile', zipFile, `${resourceName}.zip`)

  formData.append('solutionProvider', 'LOCAL')
  filter.forEach((exclusion) => formData.append('filter', exclusion))
  formData.append('userName', userName)
  formData.append('resourceName', resourceName)

  const response = await fetch(`${backendBaseUrl}upload`, {
    method: 'POST',
    body: formData
  })

  if (!response.ok) {
    throw new Error(`HTTP error! Status: ${response.status}`)
  }
}

export async function uploadGitRepo(
  userName: string,
  resourceName: string,
  branchName: string,
  resourceURL: string,
  filters: string[]
) {
  const formData = new FormData()

  formData.append('solutionProvider', 'GIT')
  formData.append('userName', userName)
  formData.append('resourceName', resourceName)
  formData.append('branch', branchName)
  formData.append('repoUrl', resourceURL)
  filters.forEach((exclusion) => formData.append('filter', exclusion))

  const response = await fetch(`${backendBaseUrl}upload`, {
    method: 'POST',
    body: formData
  })

  if (!response.ok) {
    throw new Error(`HTTP error! Status: ${response.status}`)
  }
}

export async function runJplag(
  userName: string,
  resultName: string,
  languages: string[],
  jplagSolutionRuns: JPlagSolutionRun[],
  suffixes: string[]
) {
  const payload = {
    solutions: jplagSolutionRuns,
    languages: languages,
    resultName: resultName,
    userName: userName,
    fileSuffixes: suffixes
  }

  const response = await fetch('http://localhost:8080/run', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    throw new Error(`HTTP error! Status: ${response.status}`)
  }
}

export async function getResults(userName: string) {
  const response = await fetch(`${backendBaseUrl}results?userName=${userName}`)
  if (!response.ok) {
    throw new Error('Unable to load results from server.')
  }
  return await response.json()
}

export async function getResultForUsernameAndResultName(userName: string, resultName: string) {
  const response = await fetch(
    `${backendBaseUrl}result?userName=${userName}&resultName=${resultName}`
  )
  if (!response.ok) {
    throw new Error('Unable to load overview from server.')
  }
  return await response.json()
}

export async function getResultForResultHash(resultHash: string) {
  const response = await fetch(
    `${backendBaseUrl}result?resultHash=${resultHash}`
  )
  if (!response.ok) {
    throw new Error('Unable to load overview from server.')
  }
  return await response.json()
}

export async function deleteResultByUserNameAndResultName(resultHash: string) {
  const response = await fetch(
    `${backendBaseUrl}result?&resultHash=${resultHash}`,
    { method: 'DELETE' }
  )
  if (!response.ok) {
    throw new Error('Unable to delete result from server.')
  }
}

export type FileContentMap = Record<string, string>;
export type SubmissionMap = Record<string, FileContentMap>;

export async function fetchAndExtractZip(
  userName: string,
  resultName: string,
  firstSubmissionId: string,
  secondSubmissionId: string
): Promise<SubmissionMap> {
  try {
    const response = await fetch(`http://localhost:8080/download?userName=${userName}&resultName=${resultName}&firstSubmissionId=${firstSubmissionId}&secondSubmissionId=${secondSubmissionId}`);

    if (!response.ok) {
      throw new Error('Failed to download ZIP file from server.');
    }

    const blob = await response.blob();
    const zip = await JSZip.loadAsync(blob);

    const submissions: SubmissionMap = {};

    for (const fileName of Object.keys(zip.files)) {
      const file = zip.file(fileName);
      if (file) {
        const parts = fileName.split('/');
        if (parts.length > 1) {
          const submissionId = parts[0];
          if (!submissions[submissionId]) {
            submissions[submissionId] = {};
          }
          const fileContent = await file.async('string');
          submissions[submissionId][fileName] = fileContent;
        }
      }
    }
    return submissions;
  } catch (error) {
    console.error('Error processing ZIP file:', error);
    throw new Error('Unable to process ZIP file.');
  }
}

export async function getSubmissionComparison(
  userName: string,
  resultName: string,
  firstSubmissionId: string,
  secondSubmissionId: string
) {
  const response = await fetch(
    `${backendBaseUrl}comparison?userName=${userName}&resultName=${resultName}&firstSubmissionId=${firstSubmissionId}&secondSubmissionId=${secondSubmissionId}`
  )
  if (!response.ok) {
    throw new Error('Unable to load comparison from server.')
  }
  return await response.json()
}

export async function getPlagConfigsForUser(
  userName: string,
) {
  const response = await fetch(`${backendBaseUrl}plag/config/all?userName=${userName}`)
  if (!response.ok) {
    throw new Error('Unable to load plagiarism configurations from server.')
  }
  return await response.json()
}

export async function createPlagConfig(
  userName: string,
  name: string,
  resourcesTTL: number,
  clientBackendURL: string,
  suffixes: string[],
  languages: string[],
  scheduleType: string,
  disallowedFiles: string[],
  subjectId: string,
){
  const payload = {
    name: name,
    resourcesTTL: resourcesTTL,
    clientBackendURL: clientBackendURL,
    suffixes: suffixes,
    languages: languages,
    scheduleType: scheduleType,
    userName: userName,
    disallowedFiles: disallowedFiles,
    subjectId: subjectId
  }
  const response = await fetch(`${backendBaseUrl}plag/config/basic`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  })
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${await response.text()}`);
  }
}

export async function getPlagConfigByUsernameAndName(
  userName: string,
  configName: string,
) {
  const response = await fetch(`${backendBaseUrl}plag/config?configName=${configName}&userName=${userName}`)
  if (!response.ok) {
    throw new Error('Unable to load plagiarism configuration from server.')
  }
  return await response.json()
}

export async function startjPlagRunForConfigAndProjectAndUserName(
  userName: string,
  configName: string,
  projectName: string,
) {
  const response = await fetch(`${backendBaseUrl}plag/rundetection?userName=${userName}&configName=${configName}&projectName=${projectName}`);
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Failed to start JPlag run.');
  }
  return response;
}

export async function checkIfResourceUpdating(
  userName: string,
  configName: string,
  projectName: string,
  resourcePath: string
) {
  const response = await fetch(`${backendBaseUrl}plag/config/checkresourceupdating?userName=${userName}&configName=${configName}&projectName=${projectName}&resourcePath=${resourcePath}`);
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || '');
  }
  return await response.json();
}

export async function checkIfJplagIsRunning(
  resultHash: string,
) {
  const response = await fetch(`${backendBaseUrl2}plag/checkjplagrunning?resultHash=${resultHash}`);
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || '');
  }
  return await response.json();
}

export async function checkIfBasicJplagIsRunning(
  resultHash: string,
) {
  const response = await fetch(`${backendBaseUrl2}plag/checkjplagrunning?resultHash=${resultHash}`);
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || '');
  }
  return await response.json();
}

export async function manuallyUpdateResourceApiCall(
  resourcePath: string,
  userName: string,
  configName: string,
  projectName: string
) {
  const payload = {
    resourcePath: resourcePath,
    userName: userName,
    configName: configName,
    projectName: projectName
  }
  const response = await fetch(`${backendBaseUrl}plag/config/updateResource`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  })
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom ručnog ažuriranja resursa.');
  }
  return response
}

export async function deleteResourceForProject(
  userName: string,
  configName: string,
  projectName: string,
  resourcePath: string
) {
  const response = await fetch(`${backendBaseUrl}plag/config/deleteResource?userName=${userName}&configName=${configName}&projectName=${projectName}&resourcePath=${resourcePath}`, {
    method: 'DELETE'
  })
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom brisanja resursa.');
  }
  return response
}

export async function addZipResourceToProject(
  zipFilePath,
  userName: string,
  filters: string[],
  configName: string,
  projectName: string,
  academicYear: string,
  subjectName: string,
) {
  const formData = new FormData()
  const zipName = 'addResourceManuallyToProject.zip'
  const zipFile = new File([zipFilePath], zipName, { type: 'application/zip' })
  formData.append('resourceType', 'ZIP')
  formData.append('zipFile', zipFile, zipName)
  filters.forEach((exclusion) => formData.append('filter', exclusion))
  formData.append('userName', userName)
  formData.append('configName', configName)
  formData.append('projectName', projectName)
  formData.append('academicYear', academicYear)
  formData.append('subjectName', subjectName)

  const response = await fetch(`${backendBaseUrl}plag/config/manuallyAddZipResource`, {
    method: 'POST',
    body: formData
  })

  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom dodavanja resursa.');
  }
}

export async function addGitResourceToProject(
  repoURL: string,
  branch: string,
  userName: string,
  filters: string[],
  configName: string,
  projectName: string,
  academicYear: string,
  subjectName: string,
) {
  const formData = new FormData()
  formData.append('resourceType', 'GIT')
  formData.append('branch', branch)
  formData.append('repoURL', repoURL)
  filters.forEach((exclusion) => formData.append('filter', exclusion))
  formData.append('userName', userName)
  formData.append('configName', configName)
  formData.append('projectName', projectName)
  formData.append('academicYear', academicYear)
  formData.append('subjectName', subjectName)

  const response = await fetch(`${backendBaseUrl}plag/config/manuallyAddGitResource`, {
    method: 'POST',
    body: formData
  })

  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom dodavanja resursa.');
  }
}

export async function getAllRunsForConfigAndProject(
  userName: string,
  configName: string,
  projectName: string
) {
  const response = await fetch(`${backendBaseUrl}plag/config/results?userName=${userName}&configName=${configName}&projectName=${projectName}`)
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom učitavanja svih provjera plagijata za zadani projekt.');
  }
  return await response.json()
}

export async function addAIResourceToTheProject(
  userName: string,
  configName: string,
  projectName: string,
  subjectName: string,
  language: string,
  taskText: string,
) {
  const formData = new FormData()
  formData.append('userName', userName)
  formData.append('configName', configName)
  formData.append('projectName', projectName)
  formData.append('subjectName', subjectName)
  formData.append('language', language)
  formData.append('taskText', taskText)
  formData.append('resourceType', 'AI')

  const response = await fetch(`${backendBaseUrl}plag/config/manuallyAddAIResource`, {
    method: 'POST',
    body: formData
  })

  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom dodavanja resursa.');
  }
}

export async function getPlagRunStatuses(
  resultHash: string,
) {
  const response = await fetch(`${backendBaseUrl2}plag/getPlagRunStatuses?resultHash=${resultHash}`)
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom učitavanja statusa provjere plagijata.');
  }
  return await response.json()
}

export async function getCurrentlyProcessingSubmissionsForRun(
  resultHash: string,
) {
  const response = await fetch(`${backendBaseUrl2}plag/getCurrentlyProcessingSubmissions?resultHash=${resultHash}`)
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom učitavanja trenutnih provjera plagijata.');
  }
  return await response.json()
}

export async function checkRunSuspended(
  resultHash: string,
): Promise<PlagSuspendedStatus> {
  const response = await fetch(`${backendBaseUrl2}plag/isSuspended?resultHash=${resultHash}`);
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom učitavanja statusa provjere plagijata.');
  }
  return await response.json() as PlagSuspendedStatus;
}

export async function getPlagRunDetails(
  resultHash: string,
) {
  const response = await fetch(`${backendBaseUrl2}plag/getPlagRunDetails?resultHash=${resultHash}`)
  if (!response.ok) {
    const errorData = await response.text();
    throw new Error(errorData || 'Pogreška prilikom učitavanja trenutnih provjera plagijata.');
  }
  return await response.json()
}

const backendBaseUrl = 'http://localhost:8080/'
const backendBaseUrl2 = 'http://localhost:9090/'
