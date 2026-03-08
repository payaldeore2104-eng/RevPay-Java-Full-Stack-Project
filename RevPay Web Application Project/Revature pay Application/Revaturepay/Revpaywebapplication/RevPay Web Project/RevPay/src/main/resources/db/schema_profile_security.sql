
-- 1. Create Sequences
CREATE SEQUENCE sec_questions_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE user_sec_answers_seq START WITH 1 INCREMENT BY 1 NOCACHE;

-- 2. Create Security Questions Table
CREATE TABLE security_questions (
    id NUMBER PRIMARY KEY,
    question VARCHAR2(255) NOT NULL
);

-- 3. Create User Security Answers Table
CREATE TABLE user_security_answers (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL,
    question_id NUMBER NOT NULL,
    answer_hash VARCHAR2(255) NOT NULL,
    CONSTRAINT fk_usa_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_usa_question FOREIGN KEY (question_id) REFERENCES security_questions(id) ON DELETE CASCADE,
    -- A user should only answer a specific question once
    CONSTRAINT uq_user_question UNIQUE (user_id, question_id)
);

-- 4. Insert Default Security Questions
INSERT INTO security_questions (id, question) VALUES (sec_questions_seq.NEXTVAL, 'What was the name of your first pet?');
INSERT INTO security_questions (id, question) VALUES (sec_questions_seq.NEXTVAL, 'What is your mother''s maiden name?');
INSERT INTO security_questions (id, question) VALUES (sec_questions_seq.NEXTVAL, 'In what city were you born?');
INSERT INTO security_questions (id, question) VALUES (sec_questions_seq.NEXTVAL, 'What was the name of your first elementary school?');
INSERT INTO security_questions (id, question) VALUES (sec_questions_seq.NEXTVAL, 'What is the name of your favorite childhood friend?');

COMMIT;
