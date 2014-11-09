package nachos.vm;

public class IPair {
	public IPair(int first, int second)
	{
		this.first = first;
		this.second = second;
	}
	public int first, second;
	@Override
	public String toString()
	{
		return String.valueOf(first) + " " + String.valueOf(second);
	}
	@Override
	public int hashCode()
	{
		return toString().hashCode();
	}
	@Override
	public boolean equals(Object o)
	{
		if (o == null || !(o instanceof IPair)) return false;
		IPair p = (IPair) o;
		return (p.first == first && p.second == second);
	}
}
