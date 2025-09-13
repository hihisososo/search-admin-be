package com.yjlee.search.analysis.model;

import java.util.*;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Builder
public class TokenGraph {

  @Builder.Default private Map<Integer, PositionNode> positionNodes = new TreeMap<>();
  @Builder.Default private List<TokenEdge> edges = new ArrayList<>();
  @Builder.Default private List<TokenPath> paths = new ArrayList<>();
  private String originalQuery;

  public void addToken(TokenInfo tokenInfo) {
    int startPos = tokenInfo.getPosition();
    int endPos = startPos + tokenInfo.getPositionLength();

    positionNodes.computeIfAbsent(startPos, PositionNode::new);
    positionNodes.computeIfAbsent(endPos, PositionNode::new);

    PositionNode startNode = positionNodes.get(startPos);
    startNode.addToken(tokenInfo);

    TokenEdge edge =
        TokenEdge.builder()
            .fromPosition(startPos)
            .toPosition(endPos)
            .token(tokenInfo.getToken())
            .type(tokenInfo.getType())
            .positionLength(tokenInfo.getPositionLength())
            .startOffset(tokenInfo.getStartOffset())
            .endOffset(tokenInfo.getEndOffset())
            .build();

    edges.add(edge);
  }

  public void generatePaths() {
    paths.clear();
    if (positionNodes.isEmpty()) {
      return;
    }

    Integer startPosition = positionNodes.keySet().stream().min(Integer::compareTo).orElse(0);
    Integer endPosition = positionNodes.keySet().stream().max(Integer::compareTo).orElse(0);

    List<String> currentPath = new ArrayList<>();
    Set<String> visitedPaths = new HashSet<>();

    explorePaths(startPosition, endPosition, currentPath, visitedPaths);
  }

  private void explorePaths(
      Integer currentPos, Integer targetPos, List<String> currentPath, Set<String> visitedPaths) {
    if (currentPos.equals(targetPos)) {
      if (!currentPath.isEmpty()) {
        String pathStr = String.join(" ", currentPath);
        if (!visitedPaths.contains(pathStr)) {
          visitedPaths.add(pathStr);
          paths.add(TokenPath.builder().path(pathStr).tokens(new ArrayList<>(currentPath)).build());
        }
      }
      return;
    }

    List<TokenEdge> outgoingEdges =
        edges.stream().filter(e -> e.getFromPosition() == currentPos).collect(Collectors.toList());

    for (TokenEdge edge : outgoingEdges) {
      currentPath.add(edge.getToken());
      explorePaths(edge.getToPosition(), targetPos, currentPath, visitedPaths);
      currentPath.remove(currentPath.size() - 1);
    }
  }

  public String generateMermaidDiagram() {
    StringBuilder mermaid = new StringBuilder();
    mermaid.append("graph LR\n");
    mermaid.append("    classDef startEnd fill:#e1f5fe,stroke:#01579b,stroke-width:3px\n");
    mermaid.append("    classDef andNode fill:#fff3e0,stroke:#e65100,stroke-width:2px\n");
    mermaid.append("    classDef original stroke:#2e7d32,stroke-width:3px,color:#1b5e20\n");
    mermaid.append(
        "    classDef synonym stroke:#7b1fa2,stroke-width:2px,stroke-dasharray: 5 5,color:#4a148c\n");
    mermaid.append("\n");

    Set<Integer> hasOutgoingEdge =
        edges.stream().map(TokenEdge::getFromPosition).collect(Collectors.toSet());
    Set<Integer> allPositions = new HashSet<>();
    for (TokenEdge edge : edges) {
      allPositions.add(edge.getFromPosition());
      allPositions.add(edge.getToPosition());
    }
    Map<Integer, Integer> positionMapping = new HashMap<>();
    TreeSet<Integer> sortedPositions = new TreeSet<>(allPositions);
    int mappedPos = 0;

    for (Integer pos : sortedPositions) {
      positionMapping.put(pos, mappedPos);
      // 나가는 edge가 있거나 마지막 노드면 다음 번호로
      if (hasOutgoingEdge.contains(pos)) {
        mappedPos++;
      }
      // 끊긴 노드는 다음 노드와 같은 번호 유지
    }

    // 재매핑된 마지막 position 찾기
    Integer maxMappedPosition = positionMapping.values().stream().max(Integer::compareTo).orElse(0);

    // 재매핑된 Position 노드들을 표시
    for (Map.Entry<Integer, Integer> entry : positionMapping.entrySet()) {
      int remappedPos = entry.getValue();
      String nodeName;
      String nodeClass;

      if (remappedPos == 0) {
        nodeName = "START";
        nodeClass = "startEnd";
      } else if (remappedPos == maxMappedPosition) {
        nodeName = "END";
        nodeClass = "startEnd";
      } else {
        nodeName = "AND";
        nodeClass = "andNode";
      }

      mermaid.append(String.format("    %d((\"%s\"))\n", remappedPos, nodeName));
      mermaid.append(String.format("    class %d %s\n", remappedPos, nodeClass));
    }

    mermaid.append("\n");

    // 원본 토큰과 동의어를 분리
    List<TokenEdge> originalEdges =
        edges.stream()
            .filter(e -> !"SYNONYM".equals(e.getType()))
            .sorted(
                (a, b) -> {
                  int posCompare = Integer.compare(a.getFromPosition(), b.getFromPosition());
                  return posCompare != 0 ? posCompare : a.getToken().compareTo(b.getToken());
                })
            .collect(Collectors.toList());

    List<TokenEdge> synonymEdges =
        edges.stream()
            .filter(e -> "SYNONYM".equals(e.getType()))
            .sorted(
                (a, b) -> {
                  int posCompare = Integer.compare(a.getFromPosition(), b.getFromPosition());
                  return posCompare != 0 ? posCompare : a.getToken().compareTo(b.getToken());
                })
            .collect(Collectors.toList());

    // 원본 토큰 먼저 표시
    if (!originalEdges.isEmpty()) {
      mermaid.append("    %% Original tokens\n");
      for (TokenEdge edge : originalEdges) {
        String label = edge.getToken();
        int fromPos = positionMapping.get(edge.getFromPosition());
        int toPos = positionMapping.get(edge.getToPosition());

        if (edge.getPositionLength() > 1) {
          mermaid.append(String.format("    %d ==>|%s| %d\n", fromPos, label, toPos));
        } else {
          mermaid.append(String.format("    %d -->|%s| %d\n", fromPos, label, toPos));
        }
      }
    }

    // 동의어 표시
    if (!synonymEdges.isEmpty()) {
      mermaid.append("\n    %% Synonym tokens\n");
      for (TokenEdge edge : synonymEdges) {
        String label = edge.getToken();
        int fromPos = positionMapping.get(edge.getFromPosition());
        int toPos = positionMapping.get(edge.getToPosition());

        if (edge.getPositionLength() > 1) {
          mermaid.append(String.format("    %d -.->|%s| %d\n", fromPos, label, toPos));
        } else {
          mermaid.append(String.format("    %d -.->|%s| %d\n", fromPos, label, toPos));
        }
      }
    }

    return mermaid.toString();
  }
}
