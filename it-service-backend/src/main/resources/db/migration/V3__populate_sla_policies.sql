INSERT INTO sla_policies (priority, target_resolution_hours) VALUES 
('LOW', 72),
('MEDIUM', 24),
('HIGH', 4)
ON CONFLICT (priority) DO NOTHING;
