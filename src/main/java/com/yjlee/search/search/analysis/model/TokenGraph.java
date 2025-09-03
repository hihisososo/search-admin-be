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

    // multi-position 토큰 찾기
    List<TokenEdge> multiPositionTokens =
        edges.stream().filter(TokenEdge::isMultiPosition).collect(Collectors.toList());

    // 각 multi-position 토큰에 대해 처리
    for (TokenEdge multiPosToken : multiPositionTokens) {
      int fromPos = multiPosToken.getFromPosition();
      int toPos = multiPosToken.getToPosition();

      // 해당 position 범위에 속하는 모든 토큰 수집
      List<TokenEdge> relatedTokens =
          edges.stream()
              .filter(e -> e.getFromPosition() >= fromPos && e.getFromPosition() < toPos)
              .collect(Collectors.toList());

      if (relatedTokens.isEmpty()) {
        continue;
      }

      // 원본 텍스트 복원을 위한 offset 범위 계산
      int minOffset =
          relatedTokens.stream()
              .mapToInt(TokenEdge::getStartOffset)
              .min()
              .orElse(multiPosToken.getStartOffset());
      int maxOffset =
          relatedTokens.stream()
              .mapToInt(TokenEdge::getEndOffset)
              .max()
              .orElse(multiPosToken.getEndOffset());

      // 원본 텍스트 키 생성
      String originalKey = null;
      if (originalQuery != null && minOffset >= 0 && maxOffset <= originalQuery.length()) {
        originalKey = originalQuery.substring(minOffset, maxOffset);
      }

      // 원본 토큰과 동의어 분리
      List<String> originalTokenList = new ArrayList<>();
      List<String> synonymList = new ArrayList<>();

      // multi-position 토큰 처리
      if (multiPosToken.isSynonym()) {
        synonymList.add(multiPosToken.getToken());
      } else {
        originalTokenList.add(multiPosToken.getToken());
      }

      // 관련 토큰들을 position 순서대로 정렬하여 처리
      Map<Integer, List<String>> positionTokens = new TreeMap<>();
      for (TokenEdge token : relatedTokens) {
        if (!token.isMultiPosition()) {
          int pos = token.getFromPosition();
          if (token.isSynonym()) {
            positionTokens.computeIfAbsent(pos, k -> new ArrayList<>()).add(token.getToken());
          } else {
            positionTokens.computeIfAbsent(pos, k -> new ArrayList<>()).add(token.getToken());
          }
        }
      }

      // position별 토큰들을 합쳐서 하나의 문자열로 만들기
      if (!positionTokens.isEmpty()) {
        List<String> combinedTokens = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : positionTokens.entrySet()) {
          combinedTokens.addAll(entry.getValue());
        }

        // 동의어 매핑 생성
        if (!multiPosToken.isSynonym()) {
          // multi-position 토큰이 원본인 경우 (pc → 데스크팁)
          String combinedSynonym = String.join("", combinedTokens);
          List<String> synonyms =
              synonymExpansions.computeIfAbsent(multiPosToken.getToken(), k -> new ArrayList<>());
          synonyms.add(combinedSynonym);
        } else {
          // multi-position 토큰이 동의어인 경우
          if (originalKey != null && !originalKey.isEmpty()) {
            List<String> synonyms =
                synonymExpansions.computeIfAbsent(originalKey, k -> new ArrayList<>());
            synonyms.add(multiPosToken.getToken());
            if (!combinedTokens.isEmpty()) {
              synonyms.add(String.join("", combinedTokens));
            }
          }
        }
      } else if (multiPosToken.isSynonym() && originalKey != null && !originalKey.isEmpty()) {
        // 관련 토큰이 없는 경우
        List<String> synonyms =
            synonymExpansions.computeIfAbsent(originalKey, k -> new ArrayList<>());
        synonyms.add(multiPosToken.getToken());
      }
    }

    // 단일 position 토큰들의 동의어 처리 (multi-position이 없는 경우)
    if (multiPositionTokens.isEmpty()) {
      Map<String, List<TokenEdge>> offsetGroups =
          edges.stream()
              .collect(Collectors.groupingBy(e -> e.getStartOffset() + "-" + e.getEndOffset()));

      for (List<TokenEdge> group : offsetGroups.values()) {
        if (group.size() <= 1) continue;

        List<TokenEdge> originalTokens =
            group.stream().filter(e -> !e.isSynonym()).collect(Collectors.toList());

        List<TokenEdge> synonymTokens =
            group.stream().filter(TokenEdge::isSynonym).collect(Collectors.toList());

        if (!originalTokens.isEmpty() && !synonymTokens.isEmpty()) {
          String key = originalTokens.get(0).getToken();
          List<String> synonymList =
              synonymTokens.stream()
                  .map(TokenEdge::getToken)
                  .distinct()
                  .collect(Collectors.toList());
          synonymExpansions.put(key, synonymList);
        }
      }
    }

    // 각 키의 동의어 리스트에서 중복 제거
    Map<String, List<String>> cleanedExpansions = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : synonymExpansions.entrySet()) {
      cleanedExpansions.put(
          entry.getKey(), entry.getValue().stream().distinct().collect(Collectors.toList()));
    }

    return cleanedExpansions;
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
