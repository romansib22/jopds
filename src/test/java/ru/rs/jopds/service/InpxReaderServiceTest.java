package ru.rs.jopds.service;

import org.junit.jupiter.api.Test;
import ru.rs.jopds.model.BookAuthor;

import java.util.List;

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
}