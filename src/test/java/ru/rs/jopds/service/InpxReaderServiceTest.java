package ru.rs.jopds.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BookAuthor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InpxReaderServiceTest {

    @Test
    void parseAuthors() {
        InpxReaderService service = new InpxReaderService();
        List<BookAuthor> result = service.parseAuthors("Бабкин,Михаил,Александрович:Белянин,Андрей,Олегович:Кош,Алекс,:Перумов,Ник,:Пехов,Алексей,Юрьевич:", "[;:]");
        assertEquals(5, result.size());
        assertEquals("бабкин", result.get(0).getLastName());
        assertEquals("михаил", result.get(0).getFirstName());
        assertEquals("александрович", result.get(0).getMiddleName());
        assertEquals("Бабкин Михаил Александрович", result.get(0).getFullName());
        assertEquals("кош", result.get(2).getLastName());
        assertEquals("алекс", result.get(2).getFirstName());
        assertEquals("",result.get(2).getMiddleName());
        assertEquals("Кош Алекс",result.get(2).getFullName());
    }

    /**
     * Стриминговое чтение: книги отдаются батчами заданного размера,
     * последний батч — остаток; общее количество не теряется
     */
    @Test
    void streamInpxFileBatchesAndTotal(@TempDir Path tempDir) throws IOException {
        Path inpx = tempDir.resolve("test.inpx");
        writeTestInpx(inpx, 5);

        InpxReaderService service = new InpxReaderService();
        List<Integer> batchSizes = new ArrayList<>();
        List<Book> allBooks = new ArrayList<>();

        long total = service.streamInpxFileByPath(inpx.toString(), batch -> {
            batchSizes.add(batch.size());
            allBooks.addAll(batch);
        }, 2);

        assertEquals(5L, total);
        assertEquals(List.of(2, 2, 1), batchSizes);
        assertEquals(5, allBooks.size());
        assertEquals("Книга 1", allBooks.get(0).getTitle());
        assertEquals("Книга 5", allBooks.get(4).getTitle());
    }

    /**
     * Списковый API (предпросмотр каталога) по-прежнему возвращает все книги
     */
    @Test
    void readInpxFileByPathReturnsAllBooks(@TempDir Path tempDir) throws IOException {
        Path inpx = tempDir.resolve("test.inpx");
        writeTestInpx(inpx, 3);

        InpxReaderService service = new InpxReaderService();
        List<Book> books = service.readInpxFileByPath(inpx.toString());

        assertEquals(3, books.size());
    }

    /**
     * Собирает тестовый INPX (ZIP со structure.info и одним .inp файлом из bookCount книг)
     */
    private void writeTestInpx(Path inpx, int bookCount) throws IOException {
        String structure = String.join("\n",
                "Authors", "Genres", "Title", "Series", "SeriesNumber", "FilePath",
                "FileSize", "LibId", "Del", "Format", "Date", "Language", "AdditionalInfo");

        StringBuilder inp = new StringBuilder();
        for (int i = 1; i <= bookCount; i++) {
            inp.append("Автор,Автор,Автор\t;\tКнига ").append(i)
                    .append("\t\t\t111").append(i).append("\t\t\t0\tfb2\t2024-01-0")
                    .append(i).append("\tru\t\t\n");
        }

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(inpx), StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("structure.info"));
            zos.write(structure.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("test.inp"));
            zos.write(inp.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}