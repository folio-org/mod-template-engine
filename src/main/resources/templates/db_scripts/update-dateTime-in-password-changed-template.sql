UPDATE "template" SET jsonb = replace(jsonb::text, '{{dateTime}}', '{{detailedDateTime}}')::jsonb
WHERE id = '0ff6678f-53cd-4a32-9937-504c28f14077' AND jsonb::text LIKE '%{{dateTime}}%';
