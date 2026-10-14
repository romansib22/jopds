package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.rs.jopds.model.ImportContext;

import java.nio.file.Path;

/**
 * Асинхронный импорт INPX-каталога.
 * Импорт стриминговый: книги читаются из архива и сохраняются в БД порциями
 * (IMPORT_BATCH_SIZE штук), весь каталог никогда не находится в памяти целиком.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpxImportService {

    /** Размер батча книг: сколько книг читается из INPX и сохраняется за один заход */
    private static final int IMPORT_BATCH_SIZE = 5000;

    private final InpxReaderService inpxReaderService;
    private final BookService bookService;
    private final ScanStatusService scanStatusService;

    @Async("scanTaskExecutor")
    public void importCatalogAsync(String filePath) {
        try {
            runImport(Path.of(filePath), filePath);
        } catch (Exception e) {
            failImport(e);
        }
    }

    /**
     * Асинхронный импорт INPX-каталога из указанной директории
     * (файловая валидация и ошибки импорта доступны через /api/inpx/import/status)
     */
    @Async("scanTaskExecutor")
    public void importCatalogFromDirAsync(String directoryPath, String fileName) {
        try {
            runImport(inpxReaderService.resolveInpxPath(directoryPath, fileName), directoryPath);
        } catch (Exception e) {
            failImport(e);
        }
    }

    /**
     * Стриминговый импорт: батчи книг передаются из читателя прямо в сохранение,
     * минуя промежуточный список всех книг.
     */
    private void runImport(Path inpxPath, String statusFilePath) {
        try {
            log.info("Запуск фонового сканирования INPX: {}", inpxPath);
            long startTime = System.currentTimeMillis();

            ImportContext context = bookService.startImport();
            long[] total = {0};
            inpxReaderService.streamInpxFile(inpxPath, batch -> {
                bookService.saveBookBatch(batch, context);
                total[0] += batch.size();
                log.info("Сохранён батч: {} книг (всего: {})", batch.size(), total[0]);
            }, IMPORT_BATCH_SIZE);
            bookService.finishImport(context, total[0], statusFilePath);

            log.info("Фоновое сканирование завершено: {} книг за {} минут", total[0],
                    (System.currentTimeMillis() - startTime) / 60000);
            scanStatusService.completeSuccess("Импортировано книг: " + total[0]);
        } catch (Exception e) {
            failImport(e);
        }
    }

    /**
     * Фиксирует ошибку импорта в статусе сканирования (освобождает флаг «идёт импорт»)
     */
    private void failImport(Exception e) {
        log.error("Ошибка при фоновом сканировании: {}", e.getMessage(), e);
        scanStatusService.completeError("Ошибка при импорте: " + e.getMessage());
    }
}
