UPDATE "template" SET jsonb = replace(jsonb::text, '{{dateTime}}', '{{detailedDateTime}}')::jsonb
WHERE jsonb::text LIKE '%{{dateTime}}%';
