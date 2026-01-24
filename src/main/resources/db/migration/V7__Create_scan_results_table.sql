-- Создание таблицы результатов сканирования
CREATE TABLE IF NOT EXISTS scan_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_count BIGINT NOT NULL,
    scan_date DATETIME NOT NULL,
    file_path VARCHAR(1000)
);
