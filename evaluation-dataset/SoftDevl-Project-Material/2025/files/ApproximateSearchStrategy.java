package myy803.socialbookstore.datamodel.searchstrategies;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import myy803.socialbookstore.datamodel.Book;
import myy803.socialbookstore.formsdata.BookDto;
import myy803.socialbookstore.formsdata.SearchDto;
import myy803.socialbookstore.mappers.BookMapper;

@Component
public class ApproximateSearchStrategy implements SearchStrategy {
	
	@Autowired
	protected BookMapper bookMapper;
	

	public ArrayList<BookDto> search(SearchDto searchDto, BookMapper bookMapper) {
		ArrayList<BookDto> bookDtos = new ArrayList<BookDto>();
		
		if(searchDto.getTitle() != null) {
			List<Book> books = makeInitialListOfBooks(searchDto);
			
			boolean authorsMatch = true;
			
			for(Book book : books) {
				if(!searchDto.getAuthors().equals("")) {
					authorsMatch = checkIfAuthorsMatch(searchDto, book);
				}
				if(authorsMatch) bookDtos.add(book.buildDto());
			}
		}
		
		return bookDtos;
	}
	
	
	protected List<Book> makeInitialListOfBooks(SearchDto searchDto) {
		List<Book> books = bookMapper.findByTitleContaining(searchDto.getTitle());
		return books;
	}
	
	protected boolean checkIfAuthorsMatch(SearchDto searchDto, Book book) {
		boolean authorsMatch;
		authorsMatch = book.authorsListIncludes(searchDto.getAuthors());
		return authorsMatch;
	}
}
