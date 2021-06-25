DO $$ BEGIN
    UPDATE ${myuniversity}_${mymodule}.template
    SET jsonb = jsonb_set(jsonb, '{category}', '"AutomatedFeeFineCharge"')
    WHERE jsonb->>'category' = 'AutomatedFeeFine';
EXCEPTION WHEN OTHERS THEN
END; $$;