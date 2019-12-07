public interface URLQueue {

  /**
   * Tell whether this queue is empty or not.
   * 
   * @return <code>true</code> when this queue is empty, and <code>false</code>
   *         otherwise
   */
  boolean isEmpty();

  /**
   * Tell whether this queue is full or not.
   * 
   * @return <code>true</code> when this queue is full, and <code>false</code>
   *         otherwise
   */
  boolean isFull();

  /**
   * Add the specified URL at the end of this queue. Behavior when adding to a
   * full queue depends on the implementation.
   * 
   * @param url
   *          the url as a String
   */
  void enqueue(String url);

  /**
   * Remove and return the head element of this queue. Behavior when removing
   * from an empty queue depends on the implementation.
   * 
   * @return the element at head
   */
  String dequeue();
}
