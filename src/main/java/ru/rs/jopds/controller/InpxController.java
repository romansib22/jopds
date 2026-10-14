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
        try {
            inpxImportService.importCatalogAsync(filePath);
        } catch (Exception e) {
            // Фоновая задача не стартовала (например, исполнитель отклонил её) — освобождаем флаг,
            // иначе isScanning останется true и все последующие запуски будут получать 409 до рестарта
            scanStatusService.completeError("Не удалось запустить сканирование: " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Не удалось запустить сканирование: " + e.getMessage());
        }
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
     * Читает INPX файл из указанной директории и сохраняет каталог книг в БД.
     * Импорт выполняется в фоне (как у /import): запрос сразу возвращается с 202,
     * прогресс и ошибки — через /api/inpx/import/status.
     */
    @PostMapping("/import-from-path")
    public ResponseEntity<String> importFromPath(
            @RequestParam String directoryPath,
            @RequestParam(required = false) String fileName) {
        // Валидируем параметры до захвата флага сканирования
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body("Не указан путь к директории с INPX-файлом");
        }

        // Guard: не даём второму импорту гоняться с уже идущим
        // (раньше эндпоинт выполнял импорт синхронно в HTTP-потоке и мог
        // пересечься с фоновым: два TRUNCATE+INSERT портили каталог)
        if (!scanStatusService.tryStartScan()) {
            return ResponseEntity.status(409) // Conflict
                .body("Сканирование уже выполняется. Пожалуйста, подождите.");
        }

        try {
            inpxImportService.importCatalogFromDirAsync(directoryPath.trim(), fileName);
        } catch (Exception e) {
            // Задача не стартовала — освобождаем флаг сканирования
            scanStatusService.completeError("Не удалось запустить сканирование: " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Не удалось запустить сканирование: " + e.getMessage());
        }

        return ResponseEntity.accepted()
            .body("Сканирование запущено. Дождитесь завершения.");
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

