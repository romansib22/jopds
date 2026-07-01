package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.rs.jopds.model.Book;

import java.util.List;

/**
 * Асинхронный импорт INPX-каталога
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpxImportService {

    private final InpxReaderService inpxReaderService;
    private final BookService bookService;
    private final ScanStatusService scanStatusService;

    @Async("scanTaskExecutor")
    public void importCatalogAsync(String filePath) {
        try {
            log.info("Запуск фонового сканирования INPX: {}", filePath);
            List<Book> books = inpxReaderService.readInpxFileByPath(filePath);
            bookService.saveAllBooks(books, filePath);
            scanStatusService.completeSuccess("Импортировано книг: " + books.size());
            log.info("Фоновое сканирование завершено: {} книг", books.size());
        } catch (Exception e) {
            log.error("Ошибка при фоновом сканировании: {}", e.getMessage(), e);
            scanStatusService.completeError("Ошибка при импорте: " + e.getMessage());
        }
    }
}
