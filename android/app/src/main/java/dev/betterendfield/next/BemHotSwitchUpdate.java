package dev.betterendfield.next;

import java.io.IOException;

/** Single-worker candidate transaction; publication is supplied by the native bridge. */
final class BemHotSwitchUpdate {
    interface IndexReader {String read() throws Exception;}
    interface Preparer {String prepare(String index) throws Exception;}
    interface Publisher {boolean queue(String configuration) throws Exception;}

    private String queuedIndex;
    private String preparedIndex,preparedConfiguration;

    BemHotSwitchUpdate(String initialIndex) throws IOException {queuedIndex=BemConfigurationLimit.index(initialIndex);}

    boolean update(IndexReader reader,Preparer preparer,Publisher publisher) throws Exception {
        String candidate=BemConfigurationLimit.index(reader.read());
        if(candidate.equals(queuedIndex)) {
            preparedIndex=null;preparedConfiguration=null;return false;
        }
        if(!candidate.equals(preparedIndex)) {
            preparedIndex=null;preparedConfiguration=null;
            preparedConfiguration=BemConfigurationLimit.configuration(preparer.prepare(candidate));
            preparedIndex=candidate;
        }
        if(!candidate.equals(BemConfigurationLimit.index(reader.read()))) {
            preparedIndex=null;preparedConfiguration=null;return false;
        }
        if(!publisher.queue(preparedConfiguration)) return false;
        queuedIndex=candidate;
        preparedIndex=null;preparedConfiguration=null;
        return true;
    }
}
