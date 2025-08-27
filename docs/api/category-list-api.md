# 카테고리 리스트 조회 API

## 엔드포인트
```
GET /api/v1/evaluation/categories
```

## 설명
상품 카테고리 목록을 가나다순으로 정렬하여 반환합니다.

## Request

### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| size | Integer | N | 100 | 반환할 카테고리 최대 개수 |

### 예시
```
GET /api/v1/evaluation/categories?size=50
```

## Response

### Response Body
```json
{
  "categories": [
    {
      "name": "가구/인테리어",
      "docCount": 2341
    },
    {
      "name": "가전/TV",
      "docCount": 5678
    },
    {
      "name": "노트북/데스크탑",
      "docCount": 3456
    },
    {
      "name": "도서/음반",
      "docCount": 1234
    },
    {
      "name": "생활용품",
      "docCount": 7890
    }
  ]
}
```

### Response Fields
| 필드 | 타입 | 설명 |
|------|------|------|
| categories | Array | 카테고리 목록 (가나다순 정렬) |
| categories[].name | String | 카테고리명 |
| categories[].docCount | Long | 해당 카테고리의 상품 수 |

## HTTP Status Codes
- `200 OK`: 정상 조회
- `500 Internal Server Error`: 서버 오류

## 구현 특징
- Elasticsearch aggregation을 사용하여 카테고리별 문서 수 집계
- 한국어 Collator를 사용하여 가나다순 정렬 적용
- 카테고리가 없는 경우 빈 배열 반환

## 사용 예시

### JavaScript (Fetch API)
```javascript
const getCategories = async (size = 100) => {
  try {
    const response = await fetch(`/api/v1/evaluation/categories?size=${size}`);
    const data = await response.json();
    return data.categories;
  } catch (error) {
    console.error('카테고리 조회 실패:', error);
    return [];
  }
};

// 사용
const categories = await getCategories(50);
categories.forEach(cat => {
  console.log(`${cat.name}: ${cat.docCount}개`);
});
```

### React 컴포넌트 예시
```jsx
const CategoryList = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const response = await fetch('/api/v1/evaluation/categories');
      const data = await response.json();
      setCategories(data.categories);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div>로딩중...</div>;

  return (
    <ul>
      {categories.map(category => (
        <li key={category.name}>
          {category.name} ({category.docCount}개)
        </li>
      ))}
    </ul>
  );
};
```

## 변경 이력
- 2025-01-27: API 생성, 가나다순 정렬 기능 추가