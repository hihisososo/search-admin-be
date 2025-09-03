package com.yjlee.search.search.analysis.model;

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

    log.debug("Generated {} paths from token graph", paths.size());
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

    // 마지막 position 찾기
    Integer maxPosition = positionNodes.keySet().stream().max(Integer::compareTo).orElse(0);

    // Position 노드들을 박스로 표시
    for (Map.Entry<Integer, PositionNode> entry : positionNodes.entrySet()) {
      int pos = entry.getKey();
      String nodeName;
      if (pos == 0) {
        nodeName = "START";
      } else if (pos == maxPosition) {
        nodeName = "END";
      } else {
        nodeName = "AND";
      }
      mermaid.append(String.format("    %d[\"%s\"]\n", pos, nodeName));
    }

    // 엣지들 그룹화하여 더 깔끔하게 표시
    Map<String, List<TokenEdge>> edgeGroups =
        edges.stream()
            .collect(Collectors.groupingBy(e -> e.getFromPosition() + "-" + e.getToPosition()));

    for (Map.Entry<String, List<TokenEdge>> group : edgeGroups.entrySet()) {
      List<TokenEdge> groupEdges = group.getValue();
      int fromPos = groupEdges.get(0).getFromPosition();
      int toPos = groupEdges.get(0).getToPosition();

      for (TokenEdge edge : groupEdges) {
        String label = edge.getToken();

        // positionLength가 1보다 큰 경우 표시
        if (edge.getPositionLength() > 1) {
          // 멀티 포지션 엣지는 굵은 화살표로
          mermaid.append(String.format("    %d ===%s===> %d\n", fromPos, label, toPos));
        } else {
          // 일반 엣지
          mermaid.append(String.format("    %d -->|%s| %d\n", fromPos, label, toPos));
        }
      }
    }

    return mermaid.toString();
  }

  public String generateAsciiDiagram() {
    StringBuilder ascii = new StringBuilder();

    List<Integer> positions = new ArrayList<>(positionNodes.keySet());
    if (positions.isEmpty()) {
      return "";
    }

    ascii.append("Position: ");
    for (int i = 0; i < positions.size(); i++) {
      ascii.append(String.format("[%d]", positions.get(i)));
      if (i < positions.size() - 1) {
        ascii.append(" ─── ");
      }
    }
    ascii.append("\n");

    Map<String, List<TokenEdge>> edgesByOffset =
        edges.stream()
            .collect(Collectors.groupingBy(e -> e.getStartOffset() + "-" + e.getEndOffset()));

    for (Map.Entry<String, List<TokenEdge>> entry : edgesByOffset.entrySet()) {
      ascii.append("          ");
      for (TokenEdge edge : entry.getValue()) {
        String arrow = "SYNONYM".equals(edge.getType()) ? "┈→" : "─→";
        ascii.append(String.format("%s %s ", edge.getToken(), arrow));
      }
      ascii.append("\n");
    }

    return ascii.toString();
  }

  public Map<String, List<String>> extractSynonymExpansions() {
    Map<String, List<String>> synonymExpansions = new LinkedHashMap<>();

    // position과 offset으로 그룹화
    Map<String, List<TokenEdge>> edgeGroups =
        edges.stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.getFromPosition() + "-" + e.getStartOffset() + "-" + e.getEndOffset()));

    for (List<TokenEdge> group : edgeGroups.values()) {
      if (group.size() <= 1) {
        continue; // 동의어가 없는 경우 스킵
      }

      // 원본 토큰과 동의어 분리
      List<TokenEdge> originalTokens =
          group.stream().filter(e -> !e.isSynonym()).collect(Collectors.toList());

      List<TokenEdge> synonymTokens =
          group.stream().filter(TokenEdge::isSynonym).collect(Collectors.toList());

      if (originalTokens.isEmpty() || synonymTokens.isEmpty()) {
        continue; // 원본이나 동의어가 없으면 스킵
      }

      // 원본 토큰 키 생성
      String originalKey;
      TokenEdge firstOriginal = originalTokens.get(0);

      if (firstOriginal.isMultiPosition() && originalQuery != null) {
        // multi-position인 경우 원본 쿼리에서 텍스트 추출
        int startOffset = firstOriginal.getStartOffset();
        int endOffset = firstOriginal.getEndOffset();
        if (startOffset >= 0 && endOffset <= originalQuery.length()) {
          originalKey = originalQuery.substring(startOffset, endOffset);
        } else {
          originalKey = firstOriginal.getToken();
        }
      } else {
        // 단일 position인 경우 토큰 그대로 사용
        originalKey = firstOriginal.getToken();
      }

      // 동의어 리스트 생성
      List<String> synonymList =
          synonymTokens.stream().map(TokenEdge::getToken).distinct().collect(Collectors.toList());

      if (!synonymList.isEmpty()) {
        synonymExpansions.put(originalKey, synonymList);
      }
    }

    return synonymExpansions;
  }

  @Getter
  @Builder
  public static class TokenInfo {
    private String token;
    private String type;
    private int position;
    private int positionLength;
    private int startOffset;
    private int endOffset;
  }
}
