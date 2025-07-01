import type { FileNode } from '@/model/FileNode.ts'

type KeyMap = { [key: string]: boolean };
type FileData = { data: string, key: string };

export function extractDataStrings(nodes: FileNode[], keyMap: KeyMap): FileData[] {
  const result: FileData[] = [];

  function traverse(node: FileNode) {
    if (keyMap[node.key]) {
      result.push({ data: node.data, key: node.key });
      return;
    }

    if (node.children == undefined) {
      return;
    }

    for (const child of node.children) {
      traverse(child);
    }
  }

  for (const node of nodes) {
    traverse(node);
  }

  return result;
}

export function removeNodesByKey(nodes: FileNode[], keysToRemove: string[]): FileNode[] {
  return nodes.filter(node => {
    if (node.children) {
      node.children = removeNodesByKey(node.children, keysToRemove);
    }
    return !keysToRemove.includes(node.key);
  });
}
