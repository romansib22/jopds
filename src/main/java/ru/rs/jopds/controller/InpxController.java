package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rs.jopds.config.InpxProperties;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.ScanStatus;
import ru.rs.jopds.service.BookService;
import ru.rs.jopds.service.InpxImportService;
import ru.rs.jopds.service.InpxReaderService;
import ru.rs.jopds.service.ScanStatusService;

import java.util.List;

/**
 * REST контроллер для работы с INPX файлами и каталогом книг
 */
@RestController
@RequestMapping("/api/inpx")
@RequiredArgsConstructor
public class InpxController {
    
    private final InpxReaderService inpxReaderService;
    private final BookService bookService;
    private final InpxProperties inpxProperties;
    private final ScanStatusService scanStatusService;
    private final InpxImportService inpxImportService;
    
    /**
     * Читает INPX файл из настроенной директории и возвращает каталог книг (без сохранения в БД)
     */
    @GetMapping("/catalog")
    public ResponseEntity<?> getCatalog() {
        try {
            // Проверяем, что указан путь к INPX файлу
            if (inpxProperties.getFilePath() == null || inpxProperties.getFilePath().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("Не задан путь к inpx-файлу");
            }
            
            List<Book> books = inpxReaderService.readInpxFileByPath(inpxProperties.getFilePath());
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Читает INPX файл из настроенной директории и сохраняет каталог книг в БД
     */
    @PostMapping("/import")
    public ResponseEntity<String> importCatalog() {
        if (!scanStatusService.tryStartScan()) {
            return ResponseEntity.status(409) // Conflict
                .body("Сканирование уже выполняется. Пожалуйста, подождите.");
        }

        if (inpxProperties.getFilePath() == null || inpxProperties.getFilePath().isEmpty()) {
            scanStatusService.completeError("Не задан путь к inpx-файлу");
            return ResponseEntity.badRequest()
                .body("Не задан путь к inpx-файлу");
        }

        String filePath = inpxProperties.getFilePath();
        inpxImportService.importCatalogAsync(filePath);
        return ResponseEntity.accepted()
            .body("Сканирование запущено. Дождитесь завершения.");
    }
    
    /**
     * Проверяет статус процесса сканирования
     */
    @GetMapping("/import/status")
    public ResponseEntity<ScanStatus> getScanStatus() {
        return ResponseEntity.ok(scanStatusService.getStatus());
    }
    
    /**
     * Читает INPX файл из указанной директории (без сохранения в БД)
     * 
     * @param directoryPath путь к директории с INPX файлами
     * @param fileName имя INPX файла (опционально)
     * @return список книг из каталога
     */
    @GetMapping("/read")
    public ResponseEntity<List<Book>> readInpxFile(
            @RequestParam String directoryPath,
            @RequestParam(required = false) String fileName) {
        try {
            List<Book> books = inpxReaderService.readInpxFile(directoryPath, fileName);
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Читает INPX файл из указанной директории и сохраняет каталог книг в БД
     */
    @PostMapping("/import-from-path")
    public ResponseEntity<String> importFromPath(
            @RequestParam String directoryPath,
            @RequestParam(required = false) String fileName) {
        try {
            List<Book> books = inpxReaderService.readInpxFile(directoryPath, fileName);
            bookService.saveAllBooks(books);
            return ResponseEntity.ok("Импортировано книг: " + books.size());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Ошибка при импорте: " + e.getMessage());
        }
    }
    
    /**
     * Получает все книги из БД
     */
    @GetMapping("/books")
    public ResponseEntity<List<Book>> getAllBooks() {
        try {
            List<Book> books = bookService.getAllBooks();
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Поиск книг по названию
     */
    @GetMapping("/books/search")
    public ResponseEntity<List<Book>> searchBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String format) {
        try {
            List<Book> books;
            if (title != null && !title.isEmpty()) {
                books = bookService.searchByTitle(title);
            } else if (series != null && !series.isEmpty()) {
                books = bookService.getBooksBySeries(series);
            } else if (language != null && !language.isEmpty()) {
                books = bookService.getBooksByLanguage(language);
            } else if (format != null && !format.isEmpty()) {
                books = bookService.getBooksByFormat(format);
            } else {
                books = bookService.getAllBooks();
            }
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Получает количество книг в БД
     */
    @GetMapping("/books/count")
    public ResponseEntity<Long> getBookCount() {
        try {
            return ResponseEntity.ok(bookService.getBookCount());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

