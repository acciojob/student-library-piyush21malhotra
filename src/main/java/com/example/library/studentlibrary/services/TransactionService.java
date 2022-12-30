package com.example.library.studentlibrary.services;

import com.example.library.studentlibrary.models.*;
import com.example.library.studentlibrary.repositories.BookRepository;
import com.example.library.studentlibrary.repositories.CardRepository;
import com.example.library.studentlibrary.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService {

    @Autowired
    BookRepository bookRepository5;

    @Autowired
    CardRepository cardRepository5;

    @Autowired
    TransactionRepository transactionRepository5;

    @Value("${books.max_allowed}")
    int max_allowed_books;

    @Value("${books.max_allowed_days}")
    int getMax_allowed_days;

    @Value("${books.fine.per_day}")
    int fine_per_day;

    public String issueBook(int cardId, int bookId) throws Exception {
        Book book = bookRepository5.findById(bookId).get();

        Transaction transaction = new Transaction();
        transaction.setBook(book);
        transaction.setCard(cardRepository5.findById(cardId).get());
        transaction.setIssueOperation(false);
        //check whether bookId and cardId already exist
        //conditions required for successful transaction of issue book:
        //1. book is present and available
        // If it fails: throw new Exception("Book is either unavailable or not present");
        if(!bookRepository5.existsById(bookId)) {
            transaction.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction);
            throw new Exception("Book is either unavailable or not present");
        }
        else {
            if(!bookRepository5.findById(bookId).get().isAvailable()) {
                transaction.setTransactionStatus(TransactionStatus.FAILED);
                transactionRepository5.save(transaction);
                throw new Exception("Book is either unavailable or not present");
            }
        }
        //2. card is present and activated
        // If it fails: throw new Exception("Card is invalid");
        if(!cardRepository5.existsById(cardId)) {
            transaction.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction);
            throw new Exception("Card is invalid");
        }
        else {
            if(cardRepository5.findById(cardId).get().getCardStatus() == CardStatus.DEACTIVATED) {
                transaction.setTransactionStatus(TransactionStatus.FAILED);
                transactionRepository5.save(transaction);
                throw new Exception("Card is invalid");
            }
        }
        //3. number of books issued against the card is strictly less than max_allowed_books
        // If it fails: throw new Exception("Book limit has reached for this card");
        if(cardRepository5.findById(cardId).get().getBooks().size() > max_allowed_books) {
            transaction.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction);
            throw new Exception("Book limit has reached for this card");
        }
        //If the transaction is successful, save the transaction to the list of transactions and return the id
        transaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        transaction.setIssueOperation(true);
        transactionRepository5.save(transaction);

        book.setAvailable(false);
        book.setCard(cardRepository5.findById(cardId).get());
        bookRepository5.updateBook(book);

        //Note that the error message should match exactly in all cases

       return transaction.getTransactionId(); //return transactionId instead
    }

    public Transaction returnBook(int cardId, int bookId) throws Exception{

        List<Transaction> transactions = transactionRepository5.find(cardId, bookId,TransactionStatus.SUCCESSFUL, true);
        Transaction transaction = transactions.get(transactions.size() - 1);

        //for the given transaction calculate the fine amount considering the book has been returned exactly when this function is called
        Date issueDate = transaction.getTransactionDate();
        long returnTime = Math.abs(System.currentTimeMillis() - issueDate.getTime());
        long no_of_days_passed = TimeUnit.DAYS.convert(returnTime, TimeUnit.MILLISECONDS);
        int fine = 0;
        if(no_of_days_passed > getMax_allowed_days) {
            fine = (int)(no_of_days_passed - getMax_allowed_days) * fine_per_day;
        }
        //make the book available for other users
        Book book = transaction.getBook();
        book.setAvailable(true);
        book.setCard(null);

        //make a new transaction for return book which contains the fine amount as well
        Transaction returnBookTransaction  = new Transaction();
        returnBookTransaction.setBook(transaction.getBook());
        returnBookTransaction.setCard(transaction.getCard());
        returnBookTransaction.setIssueOperation(false);
        returnBookTransaction.setFineAmount(fine);
        returnBookTransaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);

        transactionRepository5.save(returnBookTransaction);

        return returnBookTransaction; //return the transaction after updating all details
    }
}