package mainframe.client.messages.customer;

import java.io.IOException;
import mainframe.client.Connection;
import mainframe.client.Message;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;

/** Adds a document record without sending or storing file contents. */
public final class AddDocumentMetadata implements Message {
    private final String customer;
    private final String fileName;
    private final String type;
    private final String uploadDate;
    private Long id;

    public AddDocumentMetadata(String customer, String fileName, String type, String uploadDate) {
        this.customer = customer;
        this.fileName = fileName;
        this.type = type;
        this.uploadDate = uploadDate;
    }

    @Override
    public Status send(Connection connection) throws IOException {
        MessageRequest request = new MessageRequest(MessageRequest.UPDATE, "customer.addDocument");
        request.setValue("customer", customer);
        request.setValue("fileName", fileName);
        // The mainframe requires these fields even for a metadata-only record.
        // No file contents are supplied, so its stored content size is zero.
        request.setValue("fileType", "application/octet-stream");
        request.setValue("size", "0");
        request.setValue("type", type);
        request.setValue("uploadDate", uploadDate);
        MessageResponse response = connection.send(request);
        if (response.getStatus().getWasSuccessful()) {
            String responseId = response.getValue("id");
            id = responseId == null ? null : Long.valueOf(responseId);
        }
        return response.getStatus();
    }

    public Long getId() {
        return id;
    }
}
