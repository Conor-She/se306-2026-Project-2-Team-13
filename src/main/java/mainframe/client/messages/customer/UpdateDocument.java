package mainframe.client.messages.customer;

import java.io.IOException;
import mainframe.client.Connection;
import mainframe.client.Message;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;

/** Updates the metadata for an existing customer document. */
public final class UpdateDocument implements Message {
    private final String customer;
    private final long id;
    private final String fileName;
    private final String type;
    private final String uploadDate;

    public UpdateDocument(String customer, long id, String fileName, String type,
            String uploadDate) {
        this.customer = customer;
        this.id = id;
        this.fileName = fileName;
        this.type = type;
        this.uploadDate = uploadDate;
    }

    @Override
    public Status send(Connection connection) throws IOException {
        MessageRequest request = new MessageRequest(MessageRequest.UPDATE,
                "customer.updateDocument");
        request.setValue("customer", customer);
        request.setValue("id", Long.toString(id));
        request.setValue("fileName", fileName);
        request.setValue("type", type);
        request.setValue("uploadDate", uploadDate);
        MessageResponse response = connection.send(request);
        return response.getStatus();
    }
}
