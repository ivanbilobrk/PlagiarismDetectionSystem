import { Comparison } from '../Comparison'
import type { Match } from '../Match'
import { store } from '@/stores/store'
import { getMatchColorCount } from '@/utils/ColorUtils'
import slash from 'slash'
import { BaseFactory } from './BaseFactory'
import { MetricType } from '../MetricType'
import type { SubmissionFile } from '../File'
import { fetchAndExtractZip, getSubmissionComparison } from '@/api/apicalls.ts'

/**
 * Factory class for creating Comparison objects
 */
export class ComparisonFactory extends BaseFactory {
  public static async getComparison(searchKey: string): Promise<Comparison> {
    return await this.extractComparison(searchKey)
  }

  /**
   * Creates a comparison object from a json object created by JPlag
   * @param json the json object
   */
  private static async extractComparison(searchKey: string): Promise<Comparison> {
    const firstSubmissionId = searchKey.split('-')[0]
    const secondSubmissionId = searchKey.split('-')[1]
    const submissionComparison = await getSubmissionComparison(store().state.userName, store().state.resultHash, firstSubmissionId, secondSubmissionId)
    const submissionFiles = await fetchAndExtractZip(store().state.userName, store().state.resultHash, firstSubmissionId, secondSubmissionId)
    await this.loadSubmissionFiles(firstSubmissionId, submissionFiles[firstSubmissionId])
    await this.loadSubmissionFiles(secondSubmissionId, submissionFiles[secondSubmissionId])
    const filesOfFirstSubmission = store().filesOfSubmission(firstSubmissionId)
    const filesOfSecondSubmission = store().filesOfSubmission(secondSubmissionId)

    const matches = (submissionComparison.matches as Array<Match>).map((match) => {
       return { ...match, firstFile: slash(match.firstFile), secondFile: slash(match.secondFile) }
     })

    matches.forEach((match) => {
      const fileOfFirst = store().getSubmissionFile(
        firstSubmissionId,
        slash(match.firstFile as string)
      )
      const fileOfSecond = store().getSubmissionFile(
        secondSubmissionId,
        slash(match.secondFile as string)
      )

      if (fileOfFirst == undefined || fileOfSecond == undefined) {
        throw new Error(
          `The report viewer expected to find the file ${fileOfFirst == undefined ? match.firstFile : match.secondFile} in the submissions, but did not find it.`
        )
      }

      fileOfFirst.matchedTokenCount += match.tokens
      fileOfSecond.matchedTokenCount += match.tokens
    })

    return new Comparison(
      firstSubmissionId,
      secondSubmissionId,
      this.extractSimilarities(submissionComparison.similarities as Record<string, number>),
      this.getFilesWithDisplayNames(filesOfFirstSubmission),
      this.getFilesWithDisplayNames(filesOfSecondSubmission),
      this.colorMatches(matches),
      submissionComparison.first_similarity as number,
      submissionComparison.second_similarity as number
    )
  }

  private static extractSimilarities(json: Record<string, number>): Record<MetricType, number> {
    const similarities = {} as Record<MetricType, number>
    for (const [key, value] of Object.entries(json)) {
      similarities[key as MetricType] = value
    }
    return similarities
  }

  private static getSubmissionFileList(
    submissionId: string
  ): Record<string, { token_count: number }> {
    return store().state.submissionFileIndex[submissionId]
  }

  private static async loadSubmissionFiles(submissionId: string, submissionFiles: Record<string, string>) {
    try {
      const submissionFileList = this.getSubmissionFileList(submissionId)
      const fileNames = Object.keys(submissionFileList)
      for (const filePath of fileNames) {
        store().saveSubmissionFile({
          fileName: slash(filePath),
          submissionId: submissionId,
          data: submissionFiles[filePath],
          tokenCount: submissionFileList[filePath].token_count,
          matchedTokenCount: 0,
          displayFileName: slash(filePath)
        })
      }
    } catch (e) {
      console.error(e)
    }
    // try {
    //   const fileList = await this.getSubmissionFileList(submissionId)
    //   const fileNames = Object.keys(fileList)
    //   for (const filePath of fileNames) {
    //     store().saveSubmissionFile({
    //       fileName: slash(filePath),
    //       submissionId: submissionId,
    //       data: await this.getSubmissionFileContent(submissionId, slash(filePath)),
    //       tokenCount: fileList[filePath].token_count,
    //       matchedTokenCount: 0,
    //       displayFileName: slash(filePath)
    //     })
    //   }
    // } catch (e) {
    //   console.error(e)
    // }
  }

  private static async getSubmissionFileContent(submissionId: string, fileName: string) {
    if (store().state.localModeUsed && !store().state.zipModeUsed) {
      return await this.getLocalFile('files/' + fileName).then((file) => file.text())
    }
    const file = store().getSubmissionFile(submissionId, fileName)
    if (file == undefined) {
      throw new Error(
        `The report viewer expected to find the file ${fileName} in the submissions, but did not find it.`
      )
    }
    return file.data
  }

  private static colorMatches(matches: Match[]): Match[] {
    const maxColorCount = getMatchColorCount()
    let currentColorIndex = 0
    const matchesFirst = Array.from(matches)
      .sort((a, b) => a.startInFirst.line - b.startInFirst.line)
      .sort((a, b) => (a.firstFile > b.firstFile ? 1 : -1))
    const matchesSecond = Array.from(matches)
      .sort((a, b) => a.startInSecond.line - b.startInSecond.line)
      .sort((a, b) => (a.secondFile > b.secondFile ? 1 : -1))
    const sortedSize = Array.from(matches).sort((a, b) => b.tokens - a.tokens)

    function isColorAvailable(matchList: Match[], index: number) {
      return (
        (index === 0 || matchList[index - 1].colorIndex !== currentColorIndex) &&
        (index === matchList.length - 1 || matchList[index + 1].colorIndex !== currentColorIndex)
      )
    }

    for (let i = 0; i < matches.length; i++) {
      const firstIndex = matchesFirst.findIndex((match) => match === matches[i])
      const secondIndex = matchesSecond.findIndex((match) => match === matches[i])
      const sortedIndex = sortedSize.findIndex((match) => match === matches[i])
      const startCounter = currentColorIndex
      while (
        !isColorAvailable(matchesFirst, firstIndex) ||
        !isColorAvailable(matchesSecond, secondIndex) ||
        !isColorAvailable(sortedSize, sortedIndex)
      ) {
        currentColorIndex = (currentColorIndex + 1) % maxColorCount

        if (currentColorIndex == startCounter) {
          // This case should never happen, this is just a safety measure
          throw currentColorIndex
        }
      }
      matches[i].colorIndex = currentColorIndex
      currentColorIndex = (currentColorIndex + 1) % maxColorCount
    }
    return sortedSize
  }

  private static getFilesWithDisplayNames(files: SubmissionFile[]): SubmissionFile[] {
    if (files.length == 1) {
      return files
    }
    let longestPrefix = files[0].fileName
    for (let i = 1; i < files.length; i++) {
      if (longestPrefix == '') {
        break
      }

      while (!files[i].fileName.startsWith(longestPrefix)) {
        longestPrefix = longestPrefix.substring(0, longestPrefix.length - 1)
      }
    }

    return files.map((f) => {
      return {
        ...f,
        displayFileName: f.fileName.substring(longestPrefix.length)
      }
    })
  }
}
