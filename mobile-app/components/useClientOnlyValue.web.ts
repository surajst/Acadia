import { useEffect, useState } from 'react';

// `useEffect` is not invoked during server rendering, meaning
// we can use this to determine if we're on the server or not.
export function useClientOnlyValue<S, C>(server: S, client: C): S | C {
  const [value, setValue] = useState<S | C>(server);
  // The setState *is* the mechanism here, not an accident: the whole point is
  // that the server render produces `server` and the client's first effect
  // swaps in `client`. Deriving it during render would defeat it -- render runs
  // on the server too, so both sides would agree and the hook would do nothing.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- hydration probe, see above
    setValue(client);
  }, [client]);

  return value;
}
